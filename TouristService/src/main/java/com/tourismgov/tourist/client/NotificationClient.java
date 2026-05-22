package com.tourismgov.tourist.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.tourismgov.tourist.dto.NotificationRequestDTO;

@FeignClient(name = "NOTIFICATION-SERVICE")
public interface NotificationClient {

    /**
     * TARGETED: Send a private notification to a specific user.
     * Hits POST /tourismgov/v1/notifications
     */
    @PostMapping("/tourismgov/v1/notifications")
    void createNotification(@RequestBody NotificationRequestDTO request);

    /**
     * BROADCAST: Send a global notification to all users.
     * Hits POST /tourismgov/v1/notifications/broadcast
     */
    @PostMapping("/tourismgov/v1/notifications/broadcast")
    void sendGlobalBroadcast(@RequestBody NotificationRequestDTO request);
}
