package com.familier.ai.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiService {

    private final WebClient webClient;

    @Value("${gemini.api-key}")
    private String API_KEY;

    @Value("${gemini.timeout:120}")
    private long timeoutSeconds;

    public GeminiService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    @CircuitBreaker(name = "gemini-api", fallbackMethod = "fallbackStreamResponse")
    @Retry(name = "gemini-api")
    public Flux<ServerSentEvent<String>> streamGenerateContent(String systemPrompt, String message) {
        String url = "/v1beta/models/gemini-2.5-flash:streamGenerateContent?key=" + API_KEY;

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", message))))
        );

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(
                        status -> status.is5xxServerError() || status == HttpStatus.TOO_MANY_REQUESTS,
                        response -> {
                            log.error("Gemini API error: status={}", response.statusCode());
                            return response.createException();
                        }
                )
                .bodyToFlux(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .<ServerSentEvent<String>>map(response -> {
                    String text = extractTextFromResponse(response);
                    return ServerSentEvent.<String>builder()
                            .data(text)
                            .build();
                })
                .doOnError(e -> log.error("Error in Gemini stream: {}", e.getMessage()))
                .concatWith(Flux.just(ServerSentEvent.<String>builder().data("[DONE.]").build()));
    }

    public Flux<ServerSentEvent<String>> fallbackStreamResponse(String systemPrompt, String message, Exception ex) {
        log.error("Circuit breaker triggered or max retries exceeded for Gemini API: {}", ex.getMessage());
        
        String fallbackMessage = "Xin lỗi, mình đang gặp vấn đề kết nối với hệ thống AI. " +
                "Vui lòng thử lại sau vài giây nhé. Mình sẽ cố gắng phục vụ bạn tốt hơn.";
        
        return Flux.just(
                ServerSentEvent.<String>builder()
                        .data(fallbackMessage)
                        .build(),
                ServerSentEvent.<String>builder()
                        .data("[DONE.]")
                        .build()
        );
    }

    private String extractTextFromResponse(Map<String, Object> response) {
        try {
            List<?> candidates = (List<?>) response.get("candidates");
            if (candidates == null || candidates.isEmpty())
                return "";

            Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
            Map<?, ?> content = (Map<?, ?>) firstCandidate.get("content");
            if (content == null)
                return "";

            List<?> parts = (List<?>) content.get("parts");
            if (parts == null || parts.isEmpty())
                return "";

            Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
            String text = (String) firstPart.get("text");

            return (text != null) ? text : "";
        } catch (Exception e) {
            log.warn("Error extracting text from Gemini response", e);
            return "";
        }
    }
}
