package com.tourismgov.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.List;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.tourismgov.notification.client.UserClient;
import com.tourismgov.notification.dto.NotificationRequestDTO;
import com.tourismgov.notification.dto.NotificationResponseDTO;
import com.tourismgov.notification.dto.UserDTO;
import com.tourismgov.notification.enums.NotificationCategory;
import com.tourismgov.notification.enums.NotificationStatus;
import com.tourismgov.notification.model.Notification;
import com.tourismgov.notification.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserClient userClient;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private NotificationRequestDTO requestDTO;
    private UserDTO mockUser;
    private Notification mockNotification;

    @BeforeEach
    void setUp() {
        requestDTO = NotificationRequestDTO.builder()
                .userId(1L)
                .subject("Test Subject")
                .message("Test Message")
                .category(NotificationCategory.COMPLIANCE)
                .build();

        mockUser = new UserDTO();
        mockUser.setUserId(1L);
        mockUser.setName("Omkar");
        mockUser.setEmail("omkar@example.com");

        mockNotification = Notification.builder()
                .notificationId(10L)
                .userId(1L)
                .subject("Test Subject")
                .message("Test Message")
                .category(NotificationCategory.COMPLIANCE)
                .status(NotificationStatus.UNREAD)
                .build();
    }

    @Test
    void testCreateNotification_Success() {
        // Stub user client call
        when(userClient.getUserById(1L)).thenReturn(mockUser);
        // Stub repository save
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenReturn(mockNotification);

        // Run the service call
        NotificationResponseDTO result = notificationService.create(requestDTO);

        // Verify asserts
        assertNotNull(result);
        assertEquals(10L, result.getNotificationId());
        assertEquals("Test Subject", result.getSubject());
        
        // Verify dependency calls
        verify(userClient, times(1)).getUserById(1L);
        verify(notificationRepository, times(1)).saveAndFlush(any(Notification.class));
        verify(emailService, times(1)).sendNotificationEmail(eq("omkar@example.com"), eq("Omkar"), anyString(), anyString());
    }

    @Test
    void testCreateNotification_UserNotFound_ProceedsWithDefaultName() {
        // Stub user client call to throw exception or return null to test non-critical fallback
        when(userClient.getUserById(1L)).thenThrow(new RuntimeException("USER-SERVICE DOWN"));
        when(notificationRepository.saveAndFlush(any(Notification.class))).thenReturn(mockNotification);

        NotificationResponseDTO result = notificationService.create(requestDTO);

        assertNotNull(result);
        assertEquals(10L, result.getNotificationId());
        verify(userClient, times(1)).getUserById(1L);
        verify(notificationRepository, times(1)).saveAndFlush(any(Notification.class));
        // Verify email is not sent because email address could not be fetched
        verify(emailService, never()).sendNotificationEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testCreateNotification_MissingUserId_ThrowsBadRequest() {
        requestDTO.setUserId(null);

        assertThrows(ResponseStatusException.class, () -> {
            notificationService.create(requestDTO);
        });

        verify(notificationRepository, never()).saveAndFlush(any(Notification.class));
    }
}
