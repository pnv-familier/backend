package com.familier.ai.dto;

import com.familier.ai.entity.SuggestionStatus;
import com.familier.ai.entity.SuggestionType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class SuggestionResponse {
    private String id;
    private String title;
    private String description;
    private SuggestionType type;
    private SuggestionStatus status;
    private Instant createdAt;
}
