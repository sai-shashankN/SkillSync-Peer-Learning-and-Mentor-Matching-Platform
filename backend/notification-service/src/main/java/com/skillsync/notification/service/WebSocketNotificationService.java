package com.skillsync.notification.service;

import com.skillsync.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebSocketNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void pushNotification(Long userId, NotificationResponse notification) {
        messagingTemplate.convertAndSend("/queue/notifications-" + userId, notification);
    }
}
