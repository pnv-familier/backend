package com.familier.ai.controller;

import com.familier.ai.dto.ChatMessageDto;
import com.familier.ai.entity.ChatMessage;
import com.familier.ai.entity.ChatSession;
import com.familier.ai.entity.Sender;
import com.familier.ai.repository.ChatMessageRepository;
import com.familier.ai.repository.ChatSessionRepository;
import com.familier.ai.service.PromptService;
import com.familier.ai.service.grpc.UserContextServiceClient;
import com.familier.ai.util.AiContextFactory;
import com.familier.grpc.UserProfileResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiController {

    private final WebClient webClient;
    private final PromptService promptService;
    private final AiContextFactory aiContextFactory;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserContextServiceClient userContextServiceClient;

    private static final String TEST_USER_ID = "user_test_01";

    @Value("${gemini.api-key}")
    private String API_KEY;

    public AiController(WebClient.Builder webClientBuilder, 
                        PromptService promptService, 
                        AiContextFactory aiContextFactory,
                        ChatSessionRepository chatSessionRepository,
                        ChatMessageRepository chatMessageRepository,
                        UserContextServiceClient userContextServiceClient) {
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.promptService = promptService;
        this.aiContextFactory = aiContextFactory;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userContextServiceClient = userContextServiceClient;
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<ServerSentEvent<String>>>> streamAiResponse(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId,
            @RequestHeader(name = "X-User-Email", required = false) String userEmail) throws Exception {

        String email = (userEmail != null && !userEmail.isEmpty()) ? userEmail : "test@example.com";

        return userContextServiceClient.getUserProfile(email)
                .flatMap(userProfile -> {
                    try {
                        String userContext = formatUserContext(userProfile);
                        Map<String, String> vars = Map.of("USER_CONTEXT", userContext);
                        String enrichedPrompt = promptService.loadSystemPrompt("virtual_member_v2", vars);

                        return getOrCreateSession(sessionId, message)
                                .map(session -> ResponseEntity.ok()
                                        .header("X-Session-Id", session.getId())
                                        .body(saveUserMessage(session.getId(), message)
                                                .flatMapMany(savedMsg -> executeAiStream(session.getId(), message, enrichedPrompt))));
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                });
    }

    private String formatUserContext(UserProfileResponse profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Full Name: ").append(profile.getFullName()).append("\n");
        sb.append("Profile Details: ").append(profile.getProfileJson());
        return sb.toString();
    }

    private Mono<ChatMessage> saveUserMessage(String sessionId, String content) {
        ChatMessage userMessage = ChatMessage.builder()
                .sessionId(sessionId)
                .sender(Sender.USER)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
        return chatMessageRepository.save(userMessage);
    }

    private Flux<ServerSentEvent<String>> executeAiStream(String sessionId, String message, String systemPrompt) {
        String url = "/v1beta/models/gemini-2.5-flash:streamGenerateContent?key=" + API_KEY;
        StringBuilder aiContentAccumulator = new StringBuilder();

        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", message)))));

        return webClient.post()
                .uri(url)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<Map<String, Object>>() {})
                .<ServerSentEvent<String>>map(response -> {
                    String text = extractTextFromResponse(response);
                    aiContentAccumulator.append(text);
                    return ServerSentEvent.<String>builder()
                            .data(text)
                            .build();
                })
                .doOnTerminate(() -> persistAiResponse(sessionId, aiContentAccumulator.toString()))
                .concatWith(Flux.just(ServerSentEvent.<String>builder().data("[DONE.]").build()));
    }

    private void persistAiResponse(String sessionId, String fullContent) {
        ChatMessage aiMessage = ChatMessage.builder()
                .sessionId(sessionId)
                .sender(Sender.AI)
                .content(fullContent)
                .timestamp(LocalDateTime.now())
                .build();
        chatMessageRepository.save(aiMessage).subscribe();
    }

    @GetMapping("/sessions")
    public Flux<ChatSession> getSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return chatSessionRepository.findAllByUserIdOrderByCreatedAtDesc(TEST_USER_ID, PageRequest.of(page, size));
    }

    @GetMapping("/history/{sessionId}")
    public Flux<ChatMessageDto> getHistory(@PathVariable String sessionId) {
        return chatMessageRepository.findAllBySessionIdOrderByTimestampAsc(sessionId)
                .map(ChatMessageDto::fromEntity);
    }

    private Mono<ChatSession> getOrCreateSession(String sessionId, String firstMessage) {
        if (sessionId != null && !sessionId.isEmpty()) {
            return chatSessionRepository.findById(sessionId);
        }
        
        String targetContext = firstMessage.length() > 30 ? firstMessage.substring(0, 30) : firstMessage;
        ChatSession newSession = ChatSession.builder()
                .userId(TEST_USER_ID)
                .target_context(targetContext)
                .createdAt(LocalDateTime.now())
                .build();
        return chatSessionRepository.save(newSession);
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
