package com.project.familierapi.notification.service;

import com.project.familierapi.notification.domain.Notification;
import com.project.familierapi.notification.domain.NotificationType;
import com.project.familierapi.notification.domain.PushToken;
import com.project.familierapi.notification.dto.NotificationResponse;
import com.project.familierapi.notification.dto.SavePushTokenRequest;
import com.project.familierapi.notification.repository.NotificationRepository;
import com.project.familierapi.notification.repository.PushTokenRepository;
import com.project.familierapi.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final PushTokenRepository pushTokenRepository;
    private final PushNotificationService pushNotificationService;

    @Transactional
    public void savePushToken(User user, SavePushTokenRequest request) {
        pushTokenRepository.findByToken(request.getToken()).ifPresentOrElse(
                existing -> {
                    existing.setUser(user);
                    existing.setPlatform(request.getPlatform());
                    pushTokenRepository.save(existing);
                },
                () -> pushTokenRepository.save(PushToken.builder()
                        .user(user)
                        .token(request.getToken())
                        .platform(request.getPlatform())
                        .build())
        );
    }

    @Transactional
    public void deletePushToken(String token) {
        pushTokenRepository.deleteByToken(token);
    }

    @Transactional
    public void createAndPush(User recipient, User actor, NotificationType type,
                              String title, String body, String referenceId) {
        if (actor != null && recipient.getId().equals(actor.getId())) return;
        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(type)
                .title(title)
                .body(body)
                .referenceId(referenceId)
                .build());
        pushNotificationService.sendToUser(recipient.getId(), title, body, type.name(), referenceId);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(String userId, String tab) {
        PageRequest limit = PageRequest.of(0, 50);
        List<NotificationType> types = switch (tab.toLowerCase()) {
            case "post" -> List.of(NotificationType.POST_COMMENT, NotificationType.POST_REACTION);
            case "lovetask" -> List.of(NotificationType.LOVE_TASK);
            case "schedule" -> List.of(NotificationType.SCHEDULE);
            case "ai" -> List.of(NotificationType.AI);
            default -> null;
        };
        List<Notification> notifications = types == null
                ? notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, limit)
                : notificationRepository.findByRecipientIdAndTypeInOrderByCreatedAtDesc(userId, types, limit);
        return notifications.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public void markAsRead(String notificationId, String userId) {
        notificationRepository.findById(notificationId)
                .filter(n -> n.getRecipient().getId().equals(userId))
                .ifPresent(n -> {
                    n.setRead(true);
                    notificationRepository.save(n);
                });
    }

    @Transactional
    public void markAllAsRead(String userId) {
        notificationRepository.markAllReadByRecipientId(userId);
    }

    public long countUnread(String userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .title(n.getTitle())
                .body(n.getBody())
                .referenceId(n.getReferenceId())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
