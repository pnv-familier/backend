package com.familier.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentionDetectionResult {
    private boolean hasMention;
    private String targetRelation; // e.g., "FATHER", "MOTHER", "SON"
    private Double confidence; // 0.0 to 1.0
    private String reasoning;
}
