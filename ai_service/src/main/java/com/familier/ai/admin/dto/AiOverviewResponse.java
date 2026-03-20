package com.familier.ai.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiOverviewResponse {
    private long totalInteractions;
    private double interactionGrowth;
    private long totalFeedbacks;
    private double feedbackTrend;
}
