package com.familier.ai.controller;

import com.familier.ai.dto.ChatMessageDto;
import com.familier.ai.entity.ChatMessage;
import com.familier.ai.entity.ChatSession;
import com.familier.ai.entity.Sender;
import com.familier.ai.repository.ChatMessageRepository;
import com.familier.ai.repository.ChatSessionRepository;
import com.familier.ai.service.ContextManagerService;
import com.familier.ai.service.GeminiService;
import com.familier.ai.service.PromptService;
import com.familier.ai.service.SummarizationService;
import com.familier.ai.service.provider.UserProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/ai")
public class AiController {

    private final GeminiService geminiService;
    private final PromptService promptService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SummarizationService summarizationService;
    private final ContextManagerService contextManagerService;

    public AiController(GeminiService geminiService,
                        PromptService promptService, 
                        ChatSessionRepository chatSessionRepository,
                        ChatMessageRepository chatMessageRepository,
                        SummarizationService summarizationService,
                        ContextManagerService contextManagerService) {
        this.geminiService = geminiService;
        this.promptService = promptService;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.summarizationService = summarizationService;
        this.contextManagerService = contextManagerService;
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<ServerSentEvent<String>>>> streamAiResponse(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String taggedUserEmail,
            @RequestHeader(name = "X-User-Email") String email) throws Exception {

        return getOrCreateSession(sessionId, message, email)
                .flatMap(session -> contextManagerService.buildVariables(email, session.getId(), message, taggedUserEmail)
                        .flatMap(variables -> {
                            try {
                                String enrichedPrompt = promptService.loadSystemPrompt("virtual_member_v3", variables);
                                return Mono.just(ResponseEntity.ok()
                                        .header("X-Session-Id", session.getId())
                                        .body(saveUserMessage(session.getId(), message)
                                                .flatMapMany(savedMsg -> executeAiStream(session.getId(), message, enrichedPrompt))));
                            } catch (Exception e) {
                                return Mono.error(e);
                            }
                        }));
    }

    private Mono<ChatMessage> saveUserMessage(String sessionId, String content) {
        ChatMessage userMessage = ChatMessage.builder()
                .sessionId(sessionId)
                .sender(Sender.USER)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
        return chatMessageRepository.save(userMessage)
                .flatMap(msg -> updateSessionLastUpdate(sessionId).thenReturn(msg));
    }

    private Mono<Void> updateSessionLastUpdate(String sessionId) {
        return chatSessionRepository.findById(sessionId)
                .flatMap(session -> {
                    session.setLastUpdate(LocalDateTime.now());
                    if ("COMPLETED".equals(session.getStatus())) {
                        session.setStatus("ACTIVE");
                    }
                    return chatSessionRepository.save(session);
                })
                .then();
    }

    private Flux<ServerSentEvent<String>> executeAiStream(String sessionId, String message, String systemPrompt) {
        StringBuilder aiContentAccumulator = new StringBuilder();

        return geminiService.streamGenerateContent(systemPrompt, message)
                .doOnNext(event -> {
                    String data = event.data();
                    if (data != null && !data.equals("[DONE.]")) {
                        aiContentAccumulator.append(data);
                    }
                })
                .doOnTerminate(() -> persistAiResponse(sessionId, aiContentAccumulator.toString()))
                .doOnError(e -> {
                    // Error is already handled by GeminiService with fallback
                    persistAiResponse(sessionId, aiContentAccumulator.toString());
                });
    }

    private void persistAiResponse(String sessionId, String fullContent) {
        if (fullContent == null || fullContent.isEmpty()) {
            return;
        }
        
        ChatMessage aiMessage = ChatMessage.builder()
                .sessionId(sessionId)
                .sender(Sender.AI)
                .content(fullContent)
                .timestamp(LocalDateTime.now())
                .build();
        chatMessageRepository.save(aiMessage)
                .flatMap(msg -> updateSessionLastUpdate(sessionId).thenReturn(msg))
                .subscribe();
    }

    @GetMapping("/sessions")
    public Flux<ChatSession> getSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(name = "X-User-Email") String email) {
        return chatSessionRepository.findAllByUserEmailOrderByCreatedAtDesc(email, PageRequest.of(page, size));
    }

    @GetMapping("/history/{sessionId}")
    public Flux<ChatMessageDto> getHistory(
            @PathVariable String sessionId,
            @RequestHeader(name = "X-User-Email") String email) {
        return chatSessionRepository.findById(sessionId)
                .filter(session -> session.getUserEmail().equals(email))
                .flatMapMany(session -> chatMessageRepository.findAllBySessionIdOrderByTimestampAsc(sessionId))
                .map(ChatMessageDto::fromEntity);
    }

    @PostMapping("/sessions/{sessionId}/summarize")
    public Mono<ResponseEntity<Void>> summarizeSession(
            @PathVariable String sessionId,
            @RequestHeader(name = "X-User-Email") String email) {
        return chatSessionRepository.findById(sessionId)
                .flatMap(session -> summarizationService.summarizeSession(sessionId, email)
                        .then(Mono.just(ResponseEntity.ok().<Void>build())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private Mono<ChatSession> getOrCreateSession(String sessionId, String firstMessage, String userEmail) {
        if (sessionId != null && !sessionId.isEmpty()) {
            return chatSessionRepository.findById(sessionId);
        }
        
        String targetContext = firstMessage.length() > 30 ? firstMessage.substring(0, 30) : firstMessage;
        ChatSession newSession = ChatSession.builder()
                .userEmail(userEmail)
                .targetContext(targetContext)
                .createdAt(LocalDateTime.now())
                .status("ACTIVE")
                .lastUpdate(LocalDateTime.now())
                .lastSummarizedAt(LocalDateTime.now())
                .build();
        return chatSessionRepository.save(newSession);
    }
}
