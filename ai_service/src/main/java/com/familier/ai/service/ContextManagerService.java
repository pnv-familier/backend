package com.familier.ai.service;

import com.familier.ai.entity.ChatSession;
import com.familier.ai.entity.UserContext;
import com.familier.ai.repository.ChatMessageRepository;
import com.familier.ai.repository.ChatSessionRepository;
import com.familier.ai.repository.UserContextRepository;
import com.familier.grpc.UserProfileResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

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

    public Mono<Map<String, String>> buildVariables(String email, String sessionId, UserProfileResponse userProfile) {
        log.debug("Building context variables for email: {}, sessionId: {}", email, sessionId);
        
        return Mono.zip(
                getUserContext(email),
                getSessionSummary(sessionId),
                getRecentMessages(sessionId, 5),
                Mono.just(userProfile)
        ).map(tuple -> {
            UserContext userContext = tuple.getT1();
            String summary = tuple.getT2();
            String recentMessages = tuple.getT3();
            UserProfileResponse profile = tuple.getT4();
            
            Map<String, String> variables = new HashMap<>();
            
            String userContextValue = formatUserContext(profile);
            variables.put("USER_CONTEXT", userContextValue);
            
            String globalContext = userContext.getGlobalContext() != null && !userContext.getGlobalContext().isEmpty()
                    ? userContext.getGlobalContext()
                    : "Chưa có dữ liệu";
            variables.put("globalContext", globalContext);
            
            String facts = buildFactsList(userContext);
            variables.put("facts", facts);
            
            String summaryValue = summary != null && !summary.isEmpty()
                    ? summary
                    : "Chưa có tóm tắt";
            variables.put("summary", summaryValue);
            
            variables.put("RECENT_MESSAGES", recentMessages);
            
            return variables;
        });
    }

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
                .map(ChatSession::getSummary)
                .defaultIfEmpty("Chưa có tóm tắt")
                .onErrorResume(e -> {
                    log.error("Failed to fetch session summary for sessionId: {}", sessionId, e);
                    return Mono.just("Chưa có tóm tắt");
                });
    }

    private String buildFactsList(UserContext userContext) {
        if (userContext.getFacts() == null || userContext.getFacts().isEmpty()) {
            return "- Chưa có thông tin cá nhân";
        }

        return userContext.getFacts().stream()
                .filter(fact -> fact.getConfidence() != null && fact.getConfidence() >= 0.7)
                .map(fact -> String.format("- %s: %s (độ tin cậy: %.0f%%)",
                        fact.getKey(),
                        fact.getValue(),
                        fact.getConfidence() * 100))
                .collect(Collectors.joining("\n"));
    }

    private String formatUserContext(UserProfileResponse profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Full Name: ").append(profile.getFullName()).append("\n");
        sb.append("Profile Details: ").append(profile.getProfileJson());
        return sb.toString();
    }

    private Mono<String> getRecentMessages(String sessionId, int limit) {
        return chatMessageRepository.findAllBySessionIdOrderByTimestampAsc(sessionId)
                .takeLast(limit)
                .map(msg -> msg.getSender().name().toLowerCase() + ": " + msg.getContent())
                .collectList()
                .map(messages -> {
                    if (messages.isEmpty()) {
                        return "Chưa có hội thoại trước đó";
                    }
                    return String.join("\n", messages);
                })
                .onErrorResume(e -> {
                    log.error("Failed to fetch recent messages for sessionId: {}", sessionId, e);
                    return Mono.just("Chưa có hội thoại trước đó");
                });
    }
}
