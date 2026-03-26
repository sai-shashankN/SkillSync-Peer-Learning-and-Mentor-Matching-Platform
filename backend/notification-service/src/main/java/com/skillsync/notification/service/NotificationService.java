package com.skillsync.notification.service;

import com.skillsync.common.dto.PagedResponse;
import com.skillsync.common.exception.ResourceNotFoundException;
import com.skillsync.common.exception.UnauthorizedException;
import com.skillsync.notification.dto.NotificationResponse;
import com.skillsync.notification.mapper.NotificationMapper;
import com.skillsync.notification.model.Notification;
import com.skillsync.notification.repository.NotificationRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final WebSocketNotificationService webSocketNotificationService;

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getNotifications(Long userId, Boolean read, Pageable pageable) {
        Page<Notification> page = read == null
                ? notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, read, pageable);

        return PagedResponse.<NotificationResponse>builder()
                .content(page.getContent().stream().map(notificationMapper::toNotificationResponse).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new UnauthorizedException("You are not allowed to access this notification");
        }

        if (Boolean.TRUE.equals(notification.getIsRead())) {
            return;
        }

        notification.setIsRead(true);
        notification.setReadAt(Instant.now());
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public Notification createNotification(
            Long userId,
            String type,
            String title,
            String message,
            String dataJson,
            String dedupeKey
    ) {
        if (dedupeKey != null && notificationRepository.existsByDedupeKey(dedupeKey)) {
            log.info("Skipping duplicate notification for dedupeKey={}", dedupeKey);
            return notificationRepository.findByDedupeKey(dedupeKey).orElse(null);
        }

        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .data(dataJson)
                .dedupeKey(dedupeKey)
                .build();

        Notification savedNotification = notificationRepository.save(notification);
        NotificationResponse response = notificationMapper.toNotificationResponse(savedNotification);
        webSocketNotificationService.pushNotification(userId, response);
        savedNotification.setDeliveredAt(Instant.now());
        return notificationRepository.save(savedNotification);
    }
}
