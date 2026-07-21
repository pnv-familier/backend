package com.familier.ai.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Replaces GeminiService. Uses Spring AI ChatClient for Gemini calls.
 * Spring AI's ToolCallingAdvisor automatically manages the multi-turn
 * function call loop — no manual recursive streaming is needed.
 */
@Service
@Slf4j
public class AiFacadeService {

    private final ChatClient chatClient;
    private final FamilyAiTools familyAiTools;

    public AiFacadeService(ChatClient.Builder chatClientBuilder, FamilyAiTools familyAiTools) {
        this.chatClient = chatClientBuilder
                .defaultTools(familyAiTools)
                .build();
        this.familyAiTools = familyAiTools;
    }

    @CircuitBreaker(name = "gemini-api", fallbackMethod = "fallbackStream")
    @Retry(name = "gemini-api")
    public Flux<ServerSentEvent<String>> streamChat(String systemPrompt, String message, String userEmail) {
        familyAiTools.setCurrentUserEmail(userEmail);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(message)
                .stream()
                .content()
                .filter(text -> text != null && !text.isBlank())
                .map(text -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data(text)
                        .build())
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder().event("done").data("[DONE]").build()))
                .doOnTerminate(familyAiTools::clearCurrentUserEmail)
                .doOnError(e -> {
                    familyAiTools.clearCurrentUserEmail();
                    log.error("[AiFacadeService] Stream error: {}", e.getMessage());
                });
    }

    public Flux<ServerSentEvent<String>> fallbackStream(String systemPrompt, String message,
                                                         String userEmail, Exception ex) {
        log.error("[AiFacadeService] Circuit breaker triggered: {}", ex.getMessage());
        familyAiTools.clearCurrentUserEmail();
        String fallback = "Xin lỗi, mình đang gặp vấn đề kết nối với hệ thống AI. "
                + "Vui lòng thử lại sau vài giây nhé.";
        return Flux.just(
                ServerSentEvent.<String>builder().event("message").data(fallback).build(),
                ServerSentEvent.<String>builder().event("done").data("[DONE]").build());
    }
}
