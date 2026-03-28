package com.project.familierapi.notification.controller;

import com.project.familierapi.notification.dto.NotificationResponse;
import com.project.familierapi.notification.dto.SavePushTokenRequest;
import com.project.familierapi.notification.service.NotificationService;
import com.project.familierapi.shared.dto.SuccessResponse;
import com.project.familierapi.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/push-token")
    public ResponseEntity<SuccessResponse<Void>> savePushToken(
            @AuthenticationPrincipal User user,
            @RequestBody SavePushTokenRequest request) {
        notificationService.savePushToken(user, request);
        return ResponseEntity.ok(new SuccessResponse<>("Push token saved successfully", null));
    }

    @DeleteMapping("/push-token")
    public ResponseEntity<SuccessResponse<Void>> deletePushToken(
            @AuthenticationPrincipal User user,
            @RequestBody SavePushTokenRequest request) {
        notificationService.deletePushToken(request.getToken());
        return ResponseEntity.ok(new SuccessResponse<>("Push token removed", null));
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<List<NotificationResponse>>> getNotifications(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "all") String tab) {
        return ResponseEntity.ok(new SuccessResponse<>("Notifications retrieved",
                notificationService.getNotifications(user.getId(), tab)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<SuccessResponse<Map<String, Long>>> getUnreadCount(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new SuccessResponse<>("Unread count retrieved",
                Map.of("count", notificationService.countUnread(user.getId()))));
    }

    @PatchMapping("/{id}/notified")
    public ResponseEntity<SuccessResponse<Void>> markAsNotified(
            @AuthenticationPrincipal User user,
            @PathVariable String id) {
        notificationService.markAsNotified(id, user.getId());
        return ResponseEntity.ok(new SuccessResponse<>("Notification marked as notified", null));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<SuccessResponse<Void>> markAsRead(
            @AuthenticationPrincipal User user,
            @PathVariable String id) {
        notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok(new SuccessResponse<>("Notification marked as read", null));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<SuccessResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal User user) {
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok(new SuccessResponse<>("All notifications marked as read", null));
    }
}
