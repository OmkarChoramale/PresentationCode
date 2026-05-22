package com.tourismgov.report.client;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tourismgov.report.dto.NotificationRequestDTO;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class NotificationClientFallback implements NotificationClient {

    @Override
    public List<NotificationRequestDTO> getUnreadNotifications(Long userId) {
        log.warn("FALLBACK: NOTIFICATION-SERVICE unavailable. Returning empty for userId: {}", userId);
        return Collections.emptyList();
    }

    @Override
    public void createNotification(NotificationRequestDTO request) {
        log.warn("FALLBACK: NOTIFICATION-SERVICE unavailable. Notification not sent: {}", request);
    }

    @Override
    public void sendGlobalBroadcast(NotificationRequestDTO request) {
        log.warn("FALLBACK: NOTIFICATION-SERVICE unavailable. Broadcast not sent.");
    }
}
