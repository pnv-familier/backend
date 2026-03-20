package com.familier.ai.admin.service;

import com.familier.ai.admin.dto.AiOverviewResponse;
import com.familier.ai.entity.Sender;
import com.familier.ai.repository.ChatMessageRepository;
import com.familier.ai.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AdminAiService {

    private final ChatMessageRepository chatMessageRepository;
    private final ReportRepository reportRepository;

    public Mono<AiOverviewResponse> getAiOverview() {
        Instant now = Instant.now();
        Instant thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);
        Instant sixtyDaysAgo = now.minus(60, ChronoUnit.DAYS);

        return Mono.zip(
                chatMessageRepository.countBySender(Sender.USER),
                chatMessageRepository.countBySenderAndTimestampBetween(Sender.USER, thirtyDaysAgo, now),
                chatMessageRepository.countBySenderAndTimestampBetween(Sender.USER, sixtyDaysAgo, thirtyDaysAgo),
                reportRepository.count(),
                reportRepository.countByReportedAtBetween(thirtyDaysAgo, now),
                reportRepository.countByReportedAtBetween(sixtyDaysAgo, thirtyDaysAgo)
        ).map(t -> AiOverviewResponse.builder()
                .totalInteractions(t.getT1())
                .interactionGrowth(calcGrowth(t.getT2(), t.getT3()))
                .totalFeedbacks(t.getT4())
                .feedbackTrend(calcGrowth(t.getT5(), t.getT6()))
                .build());
    }

    private double calcGrowth(long current, long last) {
        if (last == 0) return current > 0 ? 100.0 : 0.0;
        return Math.round(((double) (current - last) / last) * 100 * 100.0) / 100.0;
    }
}
