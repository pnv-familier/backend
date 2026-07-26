package com.familier.ai.service;

import com.familier.ai.entity.UserContext;
import com.familier.ai.repository.ChatMessageRepository;
import com.familier.ai.repository.ChatSessionRepository;
import com.familier.ai.repository.UserContextRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ContextManagerService {

    private final UserContextRepository userContextRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final VectorStore qdrantVectorStore;

    public ContextManagerService(UserContextRepository userContextRepository,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            VectorStore qdrantVectorStore) {
        this.userContextRepository = userContextRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.qdrantVectorStore = qdrantVectorStore;
    }

    public Mono<Map<String, String>> buildSessionContext(String email, String sessionId, String userMessage) {
        return buildSessionContext(null, email, sessionId, userMessage);
    }

    public Mono<Map<String, String>> buildSessionContext(String requestId, String email, String sessionId,
            String userMessage) {
        return Mono.zip(
                getUserContext(email),
                getSessionSummary(sessionId),
                getRecentMessages(sessionId, 5),
                retrieveRelevantContext(requestId, email, sessionId, userMessage)).map(tuple -> {
                    UserContext ctx = tuple.getT1();
                    String summary = tuple.getT2();
                    String recentMessages = tuple.getT3();
                    RagContext relevantHist = tuple.getT4();

                    Map<String, String> vars = new HashMap<>();
                    vars.put("USER_OVERVIEW", ctx.getUserOverview() != null && !ctx.getUserOverview().isEmpty()
                            ? ctx.getUserOverview()
                            : "Chưa có dữ liệu");
                    vars.put("USER_FACTS", buildFactsList(ctx));
                    vars.put("SESSION_SUMMARY", summary != null && !summary.isEmpty() ? summary : "Chưa có tóm tắt");
                    vars.put("RECENT_MESSAGES", recentMessages);
                    logRagContext(requestId, email, sessionId, relevantHist);
                    vars.put("RELEVANT_HISTORY", relevantHist.relevantHistory());
                    return vars;
                });
    }

    private Mono<RagContext> retrieveRelevantContext(String requestId, String email, String sessionId, String query) {
        if (query == null || query.isBlank()) {
            return Mono.just(RagContext.empty());
        }
        return Mono.fromCallable(() -> {
            int topK = 3;
            SearchRequest request = SearchRequest.builder()
                    .query(query)
                    .topK(topK)
                    .similarityThreshold(0.7)
                    .filterExpression("email == '" + email + "'")
                    .build();
            log.debug("[RAG_SEARCH] requestId={} userId={} currentSessionId={} topK={} scoreThreshold={}",
                    requestId, email, sessionId, request.getTopK(), request.getSimilarityThreshold());
            var results = qdrantVectorStore.similaritySearch(request);
            if (results == null || results.isEmpty()) {
                return RagContext.empty();
            }

            List<Document> ownedResults = results.stream()
                    .filter(document -> Objects.equals(email, metadataValue(document, "email")))
                    .toList();

            IntStream.range(0, results.size()).forEach(index -> {
                Document document = results.get(index);
                String owner = metadataValue(document, "email");
                String type = metadataValue(document, "type");
                String sourceSessionId = metadataValue(document, "sessionId");
                if (!Objects.equals(email, owner)) {
                    log.debug(
                            "[RAG_RESULT] resultCount={} rank={} pointId={} type={} sourceSessionId={} score={} contentPreview={}",
                            results.size(), index + 1, document.getId(), type, sourceSessionId, document.getScore(),
                            preview(document.getText()));
                    return;
                }
                log.debug(
                        "[RAG_RESULT] resultCount={} rank={} pointId={} type={} sourceSessionId={} score={} contentPreview={}",
                        results.size(), index + 1, document.getId(), type, sourceSessionId, document.getScore(),
                        preview(document.getText()));
            });

            String relevantHistory = ownedResults.isEmpty()
                    ? "Không có thông tin lịch sử liên quan."
                    : ownedResults.stream()
                            .map(Document::getText)
                            .collect(Collectors.joining("\n- ", "- ", ""));

            List<String> sourceSessionIds = ownedResults.stream()
                    .map(document -> metadataValue(document, "sessionId"))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            return new RagContext(results.size(), ownedResults.size(), sourceSessionIds, relevantHistory);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("Failed to retrieve RAG relevant context for user: {}", email, e);
                    return Mono.just(RagContext.empty());
                });
    }

    private void logRagContext(String requestId, String email, String sessionId, RagContext ragContext) {
        log.debug(
                "[RAG_CONTEXT] requestId={} userId={} currentSessionId={} retrievedCount={} injectedCount={} sourceSessionIds={}",
                requestId, email, sessionId, ragContext.retrievedCount(), ragContext.injectedCount(),
                ragContext.sourceSessionIds());
    }

    private String preview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String sanitized = content.replaceAll("\\s+", " ").trim();
        return sanitized.length() <= 100 ? sanitized : sanitized.substring(0, 100);
    }

    private String metadataValue(Document document, String key) {
        if (document == null || document.getMetadata() == null) {
            return null;
        }
        Object value = document.getMetadata().get(key);
        return value != null ? value.toString() : null;
    }

    private Mono<UserContext> getUserContext(String email) {
        return userContextRepository.findByEmail(email)
                .defaultIfEmpty(UserContext.builder()
                        .email(email)
                        .userOverview("Chưa có dữ liệu")
                        .build())
                .onErrorResume(e -> {
                    log.error("Failed to fetch user context for email: {}", email, e);
                    return Mono.just(UserContext.builder()
                            .email(email)
                            .userOverview("Chưa có dữ liệu")
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
                    if (messages.isEmpty())
                        return "Chưa có hội thoại trước đó";
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

    private record RagContext(int retrievedCount, int injectedCount, List<String> sourceSessionIds,
            String relevantHistory) {
        private static RagContext empty() {
            return new RagContext(0, 0, List.of(), "Không có thông tin lịch sử liên quan.");
        }
    }
}
