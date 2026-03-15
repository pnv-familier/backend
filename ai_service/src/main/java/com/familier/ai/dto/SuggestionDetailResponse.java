package com.familier.ai.dto;

import com.familier.ai.entity.SuggestionStatus;
import com.familier.ai.entity.SuggestionType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
public class SuggestionDetailResponse {
    private String id;
    private String title;
    private String description;
    private String triggerContext;
    private SuggestionType type;
    private SuggestionStatus status;
    private Map<String, Object> payload;
    private Instant createdAt;
    private Instant expiredAt;
}
