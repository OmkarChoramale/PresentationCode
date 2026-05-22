package com.tourismgov.tourist.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tourismgov.tourist.client.NotificationClient; // ✅ ADDED CLIENT IMPORT
import com.tourismgov.tourist.dto.DocumentUploadRequest;
import com.tourismgov.tourist.dto.DocumentVerifyRequest;
import com.tourismgov.tourist.dto.NotificationRequestDTO; // ✅ ADDED DTO IMPORT
import com.tourismgov.tourist.dto.TouristDocumentResponse;
import com.tourismgov.tourist.enums.Status;
import com.tourismgov.tourist.enums.VerificationStatus;
import com.tourismgov.tourist.exception.TouristErrorMessage;
import com.tourismgov.tourist.mapper.TouristMapper;
import com.tourismgov.tourist.model.Tourist;
import com.tourismgov.tourist.model.TouristDocument;
import com.tourismgov.tourist.repository.TouristDocumentRepository;
import com.tourismgov.tourist.repository.TouristRepository;
import com.tourismgov.tourist.security.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TouristDocumentServiceImpl implements TouristDocumentService {

	private final TouristDocumentRepository documentRepository;
	private final TouristRepository touristRepository;
	private final TouristMapper touristMapper; 
	private final SecurityUtils securityUtils;
	private final NotificationClient notificationClient; // ✅ ADDED INJECTED DEPENDENCY
	
	@Override
	@Transactional
	public TouristDocumentResponse uploadDocument(Long userId, DocumentUploadRequest request) {
	    Tourist tourist = touristRepository.findByUserId(userId)
	            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No tourist profile found for the current user"));
	    securityUtils.validateAccess(tourist.getUserId());
	    Long touristId = tourist.getTouristId();

	    boolean exists = tourist.getDocuments().stream()
	            .anyMatch(d -> d.getDocType().equalsIgnoreCase(request.getDocType()));
	    if (exists) {
	        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.format("Tourist already has a document of type %s", request.getDocType()));
	    }

	    String storedFileUri;
	    try {
	        if (request.getFile() != null && !request.getFile().isEmpty()) {
	            Path filePath = Paths.get("uploads", String.valueOf(touristId), System.currentTimeMillis() + "_" + request.getFile().getOriginalFilename());
	            Files.createDirectories(filePath.getParent());
	            Files.copy(request.getFile().getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
	            storedFileUri = filePath.toUri().toString(); 
	        } else if (request.getFileUri() != null && !request.getFileUri().isBlank()) {
	            String uri = request.getFileUri().trim();
	            if (uri.startsWith("http://") || uri.startsWith("https://")) {
	                storedFileUri = uri;
	            } else {
	                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, TouristErrorMessage.ERROR_INVALID_URI_PROTOCOL);
	            }
	        } else {
	            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, TouristErrorMessage.ERROR_MISSING_FILE_DATA);
	        }
	    } catch (IOException e) {
	        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, TouristErrorMessage.ERROR_FILE_SAVE_FAILED);
	    }

	    TouristDocument doc = touristMapper.toDocumentEntity(request, tourist, storedFileUri);
	    TouristDocument saved = documentRepository.save(doc);
	    syncTouristStatus(tourist);
	    
	    return touristMapper.toDocumentResponse(saved);
	}

	@Override
	@Transactional
	public TouristDocumentResponse verifyDocument(Long touristId, Long documentId, DocumentVerifyRequest request) {
		log.info("Verifying document {} for tourist {}", documentId, touristId);
		TouristDocument doc = getTouristDocumentOrThrow(touristId, documentId);
		securityUtils.validateAccess(doc.getTourist().getUserId());
		VerificationStatus newStatus;
		try {
			newStatus = VerificationStatus.valueOf(request.getStatus().toUpperCase());
		} catch (IllegalArgumentException e) {
			log.warn("Invalid verification status '{}' for document {} of tourist {}", request.getStatus(), documentId,
					touristId);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					String.format(TouristErrorMessage.ERROR_INVALID_VERIFICATION_STATUS, request.getStatus()));
		}

		doc.setVerificationStatus(newStatus);
		doc.setRemarks(request.getRemarks());
		documentRepository.save(doc);
		log.info("Document {} for tourist {} updated to status {}", documentId, touristId, newStatus);

		syncTouristStatus(doc.getTourist());
		
		// ✅ ADDED: Private targeted notification matching BookingService fault-isolation pattern
		if (VerificationStatus.VERIFIED == newStatus) {
			sendNotificationSafe(
					doc.getTourist().getUserId(), // Targeted tourist's flat system user identity
					documentId,
					"Document Verification Approved",
					String.format("Your submitted document of type '%s' has been successfully verified by administrative staff.", doc.getDocType()),
					"ACTION_REQUIRED"
			);
		} else if (VerificationStatus.REJECTED == newStatus) {
			sendNotificationSafe(
					doc.getTourist().getUserId(),
					documentId,
					"Document Verification Rejected",
					String.format("Notice: Your document of type '%s' was rejected. Remarks: %s", doc.getDocType(), doc.getRemarks()),
					"ACTION_REQUIRED"
			);
		}
		
		return touristMapper.toDocumentResponse(doc);
	}

	@Override
	public TouristDocumentResponse getDocumentMetadata(Long userId, Long documentId) {
		log.info("Fetching metadata for document {} of tourist {}", documentId, userId);
		TouristDocument doc = getTouristDocumentByUserOrThrow(userId, documentId);
		log.info("Metadata fetched successfully for document {} of tourist {}", documentId, userId);
		securityUtils.validateAccess(doc.getTourist().getUserId());
		return touristMapper.toDocumentResponse(doc);
	}

	@Override
	@Transactional
	public void deleteDocument(Long touristId, Long documentId) {
		log.info("Deleting document {} for tourist {}", documentId, touristId);
		securityUtils.validateAdminOrStaff();
		
		TouristDocument doc = getTouristDocumentOrThrow(touristId, documentId);
		Tourist tourist = doc.getTourist();
		
		try {
			String fileUri = doc.getFileUri();
			if (fileUri != null && !(fileUri.startsWith("http://") || fileUri.startsWith("https://"))) {
				Path filePath = Paths.get(URI.create(fileUri));
				Files.deleteIfExists(filePath);
				log.info("File deleted for document {} of tourist {}", documentId, touristId);
			}
		} catch (Exception e) {
			log.error("Failed to delete file for document {}: {}", documentId, e.getMessage());
		}

		tourist.getDocuments().remove(doc);
		documentRepository.delete(doc);
		log.info("Document {} deleted successfully for tourist {}", documentId, touristId);

		syncTouristStatus(tourist);
	}

	private TouristDocument getTouristDocumentOrThrow(Long touristId, Long documentId) {
		touristRepository.findById(touristId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
				String.format(TouristErrorMessage.ERROR_TOURIST_NOT_FOUND, touristId)));

		return documentRepository.findByDocumentIdAndTourist_TouristId(documentId, touristId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						String.format(TouristErrorMessage.ERROR_DOCUMENT_NOT_FOUND, documentId, touristId)));
	}

	private TouristDocument getTouristDocumentByUserOrThrow(Long userId, Long documentId) {
		Tourist tourist = touristRepository.findByUserId(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"No tourist profile found for the current user"));

		return documentRepository.findByDocumentIdAndTourist_TouristId(documentId, tourist.getTouristId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						String.format(TouristErrorMessage.ERROR_DOCUMENT_NOT_FOUND, documentId, tourist.getTouristId())));
	}
	
	private void syncTouristStatus(Tourist tourist) {
		List<TouristDocument> docs = tourist.getDocuments();
		if (docs == null || docs.isEmpty()) {
			tourist.setStatus(Status.INACTIVE);
		} else {
			boolean hasRejected = docs.stream()
					.anyMatch(d -> d.getVerificationStatus() == VerificationStatus.REJECTED);

			boolean hasPending = docs.stream()
					.anyMatch(d -> d.getVerificationStatus() == VerificationStatus.PENDING);

			boolean allVerified = docs.stream()
					.allMatch(d -> d.getVerificationStatus() == VerificationStatus.VERIFIED);

			if (hasRejected || hasPending) {
				tourist.setStatus(Status.INACTIVE);
			} else if (allVerified) {
				tourist.setStatus(Status.ACTIVE);
			} else {
				tourist.setStatus(Status.INACTIVE);
			}
		}
		touristRepository.save(tourist);
	}

	// ✅ ADDED: Private Fault-Isolation Send Method mirroring BookingService pattern
	private void sendNotificationSafe(Long userId, Long entityId, String subject, String message, String category) {
		try {
			notificationClient.createNotification(NotificationRequestDTO.builder()
					.userId(userId)
					.entityId(entityId)
					.subject(subject)
					.message(message)
					.category(category)
					.build());
			log.info("Private notification successfully dispatched to tourist user ID: {}", userId);
		} catch (Exception e) {
			log.error("Fault-Isolation Triggered: Failed to send document notification: {}", e.getMessage());
		}
	}
}