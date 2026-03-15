package com.familier.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmSuggestionResponse {
    private boolean success;
    private String suggestionId;
    private String message;
}
