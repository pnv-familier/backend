package com.familier.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmSuggestionRequest {
    private String type; // EVENT, TASK, OFFLINE
    private Object payload; // JSON object matching EventPayload, TaskPayload, or OfflineSuggestionPayload
    private String sessionId; // For context tracing
    private String triggerContext; // Original user message
}
