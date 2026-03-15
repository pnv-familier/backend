package com.familier.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestionMetadataDto {
    private String type; // EVENT, TASK, OFFLINE
    private Object payload; // EventPayload, TaskPayload, or OfflineSuggestionPayload
}
