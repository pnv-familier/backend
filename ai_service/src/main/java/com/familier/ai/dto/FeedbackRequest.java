package com.familier.ai.dto;

import com.familier.ai.entity.FeedbackType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FeedbackRequest {
    @NotNull
    private FeedbackType type;
    private String reason;
}
