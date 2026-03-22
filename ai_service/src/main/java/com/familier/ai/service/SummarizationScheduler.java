package com.familier.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SummarizationScheduler {

    private final SummarizationService summarizationService;

    @Value("${app.summarization.enabled:false}")
    private boolean enabled;

    public SummarizationScheduler(SummarizationService summarizationService) {
        this.summarizationService = summarizationService;
    }

    @Scheduled(fixedDelayString = "${scheduler.summarization.fixed-delay:1800000}")
    public void summarizeOldActiveSessions() {
        if (!enabled) {
            return;
        }

        log.info("Starting scheduled summarization task");
        summarizationService.summarizeAllOldActiveSessions();
    }
}
