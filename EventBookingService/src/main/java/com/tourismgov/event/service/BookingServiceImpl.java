package com.tourismgov.event.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tourismgov.event.client.NotificationClient;
import com.tourismgov.event.client.TouristClient;
import com.tourismgov.event.client.UserClient;
import com.tourismgov.event.dto.AuditLogRequest;
import com.tourismgov.event.dto.BookingRequest;
import com.tourismgov.event.dto.BookingResponse;
import com.tourismgov.event.dto.NotificationRequestDTO;
import com.tourismgov.event.dto.TouristDTO;
import com.tourismgov.event.dto.UpdateBookingStatusRequest;
import com.tourismgov.event.entity.Booking;
import com.tourismgov.event.entity.Event;
import com.tourismgov.event.enums.BookingStatus;
import com.tourismgov.event.exceptions.ErrorMessages;
import com.tourismgov.event.exceptions.ResourceNotFoundException;
import com.tourismgov.event.repository.BookingRepository;
import com.tourismgov.event.repository.EventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {

    private static final String RESOURCE_BOOKING = "BookingService";
    private static final String ACTION_BOOKING_CREATE = "BOOKING_CREATE";
    private static final String ACTION_BOOKING_STATUS_UPDATE = "BOOKING_STATUS_UPDATE";
    private static final String STATUS_SUCCESS = "SUCCESS";

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final TouristClient touristClient;      
    private final UserClient userClient;
    private final NotificationClient notificationClient;

    @Override
    @Transactional
    public BookingResponse createBooking(Long eventId, BookingRequest request) {
        Long touristId = request.getTouristId();
        if (touristId == null) {
            throw new IllegalArgumentException("touristId must be provided");
        }

        log.info("Creating booking for Event ID {} and Tourist ID {}", eventId, touristId);

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event", eventId));

        TouristDTO tourist;
        try {
            tourist = touristClient.getTouristById(touristId);
        } catch (Exception e) {
            log.error("Tourist not found with ID {}", touristId, e);
            throw new ResourceNotFoundException("Tourist", touristId);
        }

        if ("INACTIVE".equalsIgnoreCase(tourist.getStatus())) {
            throw new IllegalStateException(
                    "Tourist account is inactive. Please complete verification before booking."
            );
        }

       

        Booking booking = new Booking();
        booking.setEvent(event);
        booking.setTouristId(touristId);
        booking.setNumberOfTickets(request.getNumberOfTickets() != null ? request.getNumberOfTickets() : 1);
        booking.setDate(LocalDateTime.now());
        booking.setStatus(BookingStatus.CONFIRMED);

        Booking savedBooking = bookingRepository.save(booking);

        logAuditSafe(touristId, ACTION_BOOKING_CREATE, RESOURCE_BOOKING, STATUS_SUCCESS);

        sendNotificationSafe(
                touristId,
                savedBooking.getBookingId(),
                "Booking Confirmed",
                "Your booking for " + event.getTitle() + " is confirmed! and your total booked  tickets is: "+ request.getNumberOfTickets(),
                "TRANSACTIONAL"
        );

        return mapToResponse(savedBooking);
    }

    @Override
    @Transactional
    public BookingResponse updateBookingStatus(Long bookingId, UpdateBookingStatusRequest request) {
        if (request.getTouristId() == null) {
            throw new IllegalArgumentException("touristId must be provided");
        }
        if (request.getStatus() == null) {
            throw new IllegalArgumentException("status must be provided");
        }

        Long touristId = request.getTouristId();

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new IllegalStateException("Cancelled bookings cannot be updated");
        }

        if (!booking.getTouristId().equals(touristId)) {
            throw new IllegalStateException("You are not allowed to update this booking");
        }

        if (booking.getStatus() == request.getStatus()) {
            throw new IllegalArgumentException("Booking is already in status: " + booking.getStatus());
        }

        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(request.getStatus());
        Booking updatedBooking = bookingRepository.save(booking);

        // ✅ FIXED: Log audit event for status updates
        logAuditSafe(touristId, ACTION_BOOKING_STATUS_UPDATE, RESOURCE_BOOKING, STATUS_SUCCESS);

        if (!oldStatus.equals(updatedBooking.getStatus())) {
            sendNotificationSafe(
                    touristId,
                    bookingId,
                    "Booking Status Updated",
                    "Your booking status changed to " + updatedBooking.getStatus(),
                    "SYSTEM_UPDATE"
            );
        }

        return mapToResponse(updatedBooking);
    }

    @Override
    public BookingResponse getBookingById(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
    }

    @Override
    public List<BookingResponse> getBookingsByEvent(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", eventId);
        }
        return bookingRepository.findByEvent_EventId(eventId)
                .stream().map(this::mapToResponse).toList();
    }

    @Override
    public List<BookingResponse> getBookingsByTourist(Long touristId) {
        return bookingRepository.findByTouristId(touristId)
                .stream().map(this::mapToResponse).toList();
    }

    @Override
    public Page<BookingResponse> getAllBookingsPaged(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (status != null && !status.isBlank()) {
            BookingStatus enumStatus = BookingStatus.valueOf(status.toUpperCase());
            return bookingRepository.findByStatus(enumStatus, pageable).map(this::mapToResponse);
        }
        return bookingRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    public Page<BookingResponse> getBookingsByEventPaged(Long eventId, int page, int size) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("Event", eventId);
        }
        Pageable pageable = PageRequest.of(page, size);
        return bookingRepository.findByEvent_EventId(eventId, pageable).map(this::mapToResponse);
    }

    private void logAuditSafe(Long userId, String action, String resource, String status) {
        try {
            AuditLogRequest audit = new AuditLogRequest();
            audit.setUserId(userId);
            audit.setAction(action);
            audit.setResource(resource);
            audit.setStatus(status);
            userClient.logAction(audit);
        } catch (Exception e) {
            log.error("Audit logging failed", e);
        }
    }

    private void sendNotificationSafe(Long userId, Long entityId, String subject, String message, String category) {
        try {
            notificationClient.createNotification(NotificationRequestDTO.builder()
                    .userId(userId)
                    .entityId(entityId)
                    .subject(subject)
                    .message(message)
                    .category(category)
                    .build());
            log.info("Private notification dispatched to user: {}", userId);
        } catch (Exception e) {
            // ✅ ISOLATED EXCEPTION BLOCK: Notification infrastructure errors will never rollback database entities
            log.error("Fault-Isolation Triggered: Failed to send targeted notification: {}", e.getMessage());
        }
    }

    private BookingResponse mapToResponse(Booking booking) {
        BookingResponse response = new BookingResponse();
        response.setBookingId(booking.getBookingId());
        response.setTouristId(booking.getTouristId());
        response.setEventId(booking.getEvent().getEventId());
        response.setDate(booking.getDate());
        response.setNumberOfTickets(booking.getNumberOfTickets());
        response.setStatus(booking.getStatus().name());
        return response;
    }
}