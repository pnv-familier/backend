package com.familier.ai.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import com.familier.ai.service.PromptService;
import com.familier.ai.util.AiContextFactory;

import reactor.core.publisher.Flux;

@RestController
public class AiController {

    private final WebClient webClient;
    private final PromptService promptService;
    private final AiContextFactory aiContextFactory;

    @Value("${gemini.api-key}")
    private String API_KEY;

    public AiController(WebClient.Builder webClientBuilder, PromptService promptService, AiContextFactory aiContextFactory) {
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.promptService = promptService;
        this.aiContextFactory = aiContextFactory;
    }

    @GetMapping(value = "/ai/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamAiResponse(@RequestParam String message) throws Exception {
        String url = "/v1beta/models/gemini-2.5-flash:streamGenerateContent?key=" + API_KEY;

        Map<String, String> context = aiContextFactory.createCurrentContext(1L);

        String systemPrompt = promptService.loadSystemPrompt("virtual_member_v1", context);

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", message)))));

        return webClient.post()
            .uri(url)
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(Map.class)
            .map(response -> {
                String text = extractTextFromResponse(response);
                return ServerSentEvent.<String>builder()
                        .data(text)
                        .build();
            })
            .concatWith(Flux.just(ServerSentEvent.<String>builder().data("[DONE.]").build())); 
    }

    private String extractTextFromResponse(Map response) {
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
            return "";
        }
    } 
}
