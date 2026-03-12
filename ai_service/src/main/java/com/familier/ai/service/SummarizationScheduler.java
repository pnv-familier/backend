package com.familier.ai.service;

import com.familier.ai.repository.ChatSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class SummarizationScheduler {

    private final ChatSessionRepository chatSessionRepository;
    private final SummarizationService summarizationService;

    @Value("${scheduler.summarization.fixed-delay:900000}")
    private long schedulerFixedDelay;

    public SummarizationScheduler(ChatSessionRepository chatSessionRepository,
                                  SummarizationService summarizationService) {
        this.chatSessionRepository = chatSessionRepository;
        this.summarizationService = summarizationService;
    }

    @Scheduled(fixedDelayString = "${scheduler.summarization.fixed-delay:900000}")
    public void summarizeOldActiveSessions() {
        log.info("Starting scheduled summarization task");
        
        LocalDateTime thirtyMinutesAgo = LocalDateTime.now().minusMinutes(30);

        chatSessionRepository.findAllByStatusAndLastUpdateBefore("ACTIVE", thirtyMinutesAgo)
                .flatMap(session -> {
                    log.info("Summarizing session: {} for user: {}", session.getId(), session.getUserEmail());
                    return summarizationService.summarizeSession(session.getId(), session.getUserEmail())
                            .onErrorResume(e -> {
                                log.error("Failed to summarize session {}: {}", session.getId(), e.getMessage());
                                return reactor.core.publisher.Mono.empty();
                            });
                })
                .then()
                .subscribe(
                    unused -> {},
                    error -> log.error("Error during scheduled summarization: {}", error.getMessage()),
                    () -> log.info("Scheduled summarization task completed")
                );
    }
}
