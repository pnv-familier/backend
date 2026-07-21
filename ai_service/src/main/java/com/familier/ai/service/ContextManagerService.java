package com.familier.ai.service;

import com.familier.ai.entity.UserContext;
import com.familier.ai.repository.ChatMessageRepository;
import com.familier.ai.repository.ChatSessionRepository;
import com.familier.ai.repository.UserContextRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Simplified context service: fetches ONLY session-level context
 * (user facts, session summary, recent messages).
 *
 * User profile and family member profiles are fetched on-demand
 * by FamilyAiTools via @Tool function calls.
 *
 * Removed: UnifiedDetectionService, UserProvider, TargetProfileService,
 *          RelationMappingService, BuildVariablesResult, buildVariables().
 */
@Service
@Slf4j
public class ContextManagerService {

    private final UserContextRepository userContextRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ContextManagerService(UserContextRepository userContextRepository,
                                 ChatSessionRepository chatSessionRepository,
                                 ChatMessageRepository chatMessageRepository) {
        this.userContextRepository = userContextRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * Builds session-level prompt variables: globalContext, facts, summary, recent messages.
     * All other data (user profile, family member profiles) is fetched on-demand by @Tool methods.
     */
    public Mono<Map<String, String>> buildSessionContext(String email, String sessionId) {
        return Mono.zip(
                getUserContext(email),
                getSessionSummary(sessionId),
                getRecentMessages(sessionId, 5)
        ).map(tuple -> {
            UserContext ctx       = tuple.getT1();
            String summary        = tuple.getT2();
            String recentMessages = tuple.getT3();

            Map<String, String> vars = new HashMap<>();
            vars.put("globalContext", ctx.getGlobalContext() != null && !ctx.getGlobalContext().isEmpty()
                    ? ctx.getGlobalContext() : "Chưa có dữ liệu");
            vars.put("facts",          buildFactsList(ctx));
            vars.put("summary",        summary != null && !summary.isEmpty() ? summary : "Chưa có tóm tắt");
            vars.put("RECENT_MESSAGES", recentMessages);
            return vars;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers (kept from previous version)
    // ─────────────────────────────────────────────────────────────────────────

    private Mono<UserContext> getUserContext(String email) {
        return userContextRepository.findByEmail(email)
                .defaultIfEmpty(UserContext.builder()
                        .email(email)
                        .globalContext("Chưa có dữ liệu")
                        .build())
                .onErrorResume(e -> {
                    log.error("Failed to fetch user context for email: {}", email, e);
                    return Mono.just(UserContext.builder()
                            .email(email)
                            .globalContext("Chưa có dữ liệu")
                            .build());
                });
    }

    private Mono<String> getSessionSummary(String sessionId) {
        return chatSessionRepository.findById(sessionId)
                .map(session -> session.getSummary() != null ? session.getSummary() : "Chưa có tóm tắt")
                .defaultIfEmpty("Chưa có tóm tắt")
                .onErrorResume(e -> {
                    log.error("Failed to fetch session summary for sessionId: {}", sessionId, e);
                    return Mono.just("Chưa có tóm tắt");
                });
    }

    private Mono<String> getRecentMessages(String sessionId, int limit) {
        return chatMessageRepository.findAllBySessionIdOrderByTimestampAsc(sessionId)
                .takeLast(limit)
                .map(msg -> msg.getSender().name().toLowerCase() + ": " + msg.getContent())
                .collectList()
                .map(messages -> {
                    if (messages.isEmpty()) return "Chưa có hội thoại trước đó";
                    return String.join("\n", messages);
                })
                .onErrorResume(e -> {
                    log.error("Failed to fetch recent messages for sessionId: {}", sessionId, e);
                    return Mono.just("Chưa có hội thoại trước đó");
                });
    }

    private String buildFactsList(UserContext userContext) {
        if (userContext == null || userContext.getFacts() == null || userContext.getFacts().isEmpty()) {
            return "- Chưa có thông tin cá nhân";
        }
        return userContext.getFacts().stream()
                .filter(fact -> fact.getConfidence() != null && fact.getConfidence() >= 0.7)
                .sorted(java.util.Comparator.comparingDouble(UserContext.Fact::getConfidence).reversed())
                .limit(10)
                .map(fact -> {
                    boolean isTemporary = "TEMPORARY".equals(fact.getCategory());
                    return String.format("- %s: %s%s",
                            fact.getKey(), fact.getValue(),
                            isTemporary ? " [hiện tại]" : "");
                })
                .collect(Collectors.joining("\n"));
    }
}
