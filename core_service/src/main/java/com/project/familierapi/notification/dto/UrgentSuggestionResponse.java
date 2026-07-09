package com.project.familierapi.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class UrgentSuggestionResponse {
    private String id;
    private String senderName;
    private String emotion;
    private String context;
    private String subType;
    private String message;       // template message đã render sẵn
    private boolean read;
    private Instant createdAt;
}
