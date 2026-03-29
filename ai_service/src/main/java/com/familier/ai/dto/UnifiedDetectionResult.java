package com.familier.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedDetectionResult {
    private MentionDetection mention;
    private SuggestionDetection suggestion;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MentionDetection {
        private boolean hasMention;
        private String targetRelation;
        private Double confidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuggestionDetection {
        private boolean hasSuggestion;
        private String type;        // EVENT, TASK, OFFLINE
        private String subType;     // EMOTIONAL_SUPPORT, SOCIAL_ISOLATION, POSITIVE_MILESTONE, STRONG_NEGATIVE_EMOTION
        private boolean isBroadcast;
        private Double confidence;
    }
}
