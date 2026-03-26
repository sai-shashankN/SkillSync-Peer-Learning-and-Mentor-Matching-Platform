package com.skillsync.notification.controller;

import com.skillsync.common.dto.ApiResponse;
import com.skillsync.common.dto.PagedResponse;
import com.skillsync.notification.dto.NotificationResponse;
import com.skillsync.notification.dto.UnreadCountResponse;
import com.skillsync.notification.service.NotificationService;
import com.skillsync.notification.util.RequestHeaderUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PagedResponse<NotificationResponse>>> getNotifications(
            HttpServletRequest request,
            @RequestParam(required = false) Boolean read,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PagedResponse<NotificationResponse> response = notificationService.getNotifications(
                RequestHeaderUtils.extractUserId(request),
                read,
                pageable
        );
        return ResponseEntity.ok(ApiResponse.ok("Notifications fetched successfully", response));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(HttpServletRequest request, @PathVariable Long id) {
        notificationService.markAsRead(id, RequestHeaderUtils.extractUserId(request));
        return ResponseEntity.ok(ApiResponse.ok("Notification marked as read", null));
    }

    @PutMapping("/me/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(HttpServletRequest request) {
        notificationService.markAllAsRead(RequestHeaderUtils.extractUserId(request));
        return ResponseEntity.ok(ApiResponse.ok("All notifications marked as read", null));
    }

    @GetMapping("/me/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(HttpServletRequest request) {
        UnreadCountResponse response = UnreadCountResponse.builder()
                .count(notificationService.getUnreadCount(RequestHeaderUtils.extractUserId(request)))
                .build();
        return ResponseEntity.ok(ApiResponse.ok("Unread notification count fetched successfully", response));
    }
}
