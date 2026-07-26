package com.familier.ai.service;

import com.familier.ai.dto.ChatMessageDto;
import com.familier.ai.entity.ChatMessage;
import com.familier.ai.entity.ChatSession;
import com.familier.ai.entity.Sender;
import com.familier.ai.repository.ChatMessageRepository;
import com.familier.ai.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * Owns all ChatSession and ChatMessage persistence operations.
 * Controllers must not call ChatSessionRepository or ChatMessageRepository directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // Session management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the existing session by ID, or creates a new one for the user.
     */
    public Mono<ChatSession> getOrCreateSession(String sessionId, String firstMessage, String userEmail) {
        if (sessionId != null && !sessionId.isEmpty()) {
            return chatSessionRepository.findById(sessionId);
        }
        String targetContext = firstMessage.length() > 30 ? firstMessage.substring(0, 30) : firstMessage;
        ChatSession newSession = ChatSession.builder()
                .userEmail(userEmail)
                .targetContext(targetContext)
                .createdAt(Instant.now())
                .status("ACTIVE")
                .lastUpdate(Instant.now())
                .lastSummarizedAt(Instant.now())
                .build();
        return chatSessionRepository.save(newSession);
    }

    /**
     * Updates lastUpdate timestamp and reactivates a COMPLETED session.
     */
    public Mono<Void> touchSession(String sessionId) {
        return chatSessionRepository.findById(sessionId)
                .flatMap(session -> {
                    session.setLastUpdate(Instant.now());
                    if ("COMPLETED".equals(session.getStatus())) {
                        session.setStatus("ACTIVE");
                    }
                    return chatSessionRepository.save(session);
                })
                .then();
    }

    /**
     * Returns all sessions for a user, newest first.
     */
    public Flux<ChatSession> getSessionsByUser(String email, Pageable pageable) {
        return chatSessionRepository.findAllByUserEmailOrderByCreatedAtDesc(email, pageable);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Message persistence
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Saves the user's chat message and touches the session timestamp.
     */
    public Mono<ChatMessage> saveUserMessage(String sessionId, String content) {
        ChatMessage userMessage = ChatMessage.builder()
                .sessionId(sessionId)
                .sender(Sender.USER)
                .content(content)
                .suggestions(null)
                .timestamp(Instant.now())
                .build();
        return chatMessageRepository.save(userMessage)
                .flatMap(msg -> touchSession(sessionId).thenReturn(msg));
    }

    /**
     * Saves the AI's response (fire-and-forget). No-op if content is blank.
     */
    public void saveAiMessage(String sessionId, String content, List<String> suggestions) {
        if (content == null || content.isEmpty()) return;

        ChatMessage aiMessage = ChatMessage.builder()
                .sessionId(sessionId)
                .sender(Sender.AI)
                .content(content)
                .suggestions(suggestions)
                .timestamp(Instant.now())
                .build();

        chatMessageRepository.save(aiMessage)
                .flatMap(msg -> touchSession(sessionId).thenReturn(msg))
                .subscribe(
                        saved -> {},
                        error -> log.error("Failed to save AI message for session {}", sessionId, error)
                );
    }

    /**
     * Returns the full message history for a session, verified to belong to the requesting user.
     */
    public Flux<ChatMessageDto> getHistory(String sessionId, String userEmail) {
        return chatSessionRepository.findById(sessionId)
                .filter(session -> session.getUserEmail().equals(userEmail))
                .flatMapMany(session -> chatMessageRepository.findAllBySessionIdOrderByTimestampAsc(sessionId))
                .map(ChatMessageDto::fromEntity);
    }
}
