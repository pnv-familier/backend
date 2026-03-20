package com.project.familierapi.notification.dto;

import com.project.familierapi.notification.domain.NotificationType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class NotificationResponse {
    private String id;
    private NotificationType type;
    private String title;
    private String body;
    private String referenceId;
    private boolean isRead;
    private Instant createdAt;
}
