package com.tourismgov.site.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.tourismgov.site.dto.NotificationRequestDTO;

@FeignClient(name = "NOTIFICATION-SERVICE")
public interface NotificationClient {

    /**
     * TARGETED: Hits NotificationController.create (POST /notifications)
     * Used for private/targeted alerts to a specific user.
     */
    @PostMapping("/tourismgov/v1/notifications")
    void createNotification(@RequestBody NotificationRequestDTO request);

    /**
     * BROADCAST: Hits NotificationController.broadcast (POST /notifications/broadcast)
     * Used for global system-wide announcements.
     */
    @PostMapping("/tourismgov/v1/notifications/broadcast")
    void sendGlobalBroadcast(@RequestBody NotificationRequestDTO request);
}