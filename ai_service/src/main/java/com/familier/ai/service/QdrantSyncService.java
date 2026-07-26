package com.familier.ai.service;

import com.familier.ai.entity.ChatSession;
import com.familier.ai.entity.UserContext;
import com.familier.ai.repository.ChatSessionRepository;
import com.familier.ai.repository.UserContextRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class QdrantSyncService {

    private final VectorStore qdrantVectorStore;
    private final UserContextRepository userContextRepository;
    private final ChatSessionRepository chatSessionRepository;

    @Value("${app.vectorsync.batch-size:20}")
    private int batchSize;

    /**
     * Embeds all PENDING facts for a user and upserts them to Qdrant.
     * Called synchronously within the summarization cron cycle.
     */
    public Mono<Void> syncPendingFacts(String userEmail) {
        return userContextRepository.findByEmail(userEmail)
                .flatMap(ctx -> Mono.fromCallable(() -> {
                    List<UserContext.Fact> facts = ctx.getFacts() == null ? List.of() : ctx.getFacts();
                    List<UserContext.Fact> pending = facts.stream()
                            .filter(f -> "PENDING".equals(f.getQdrantSyncStatus()))
                            .limit(batchSize)
                            .toList();

                    if (pending.isEmpty()) {
                        log.debug("[QdrantSync] No PENDING facts for user={}", userEmail);
                        return ctx;
                    }

                    List<Document> docs = pending.stream()
                            .map(f -> new Document(
                                    String.format("[Đặc điểm người dùng] %s: %s", f.getKey(), f.getValue()),
                                    Map.of(
                                            "email", userEmail != null ? userEmail : "",
                                            "type", "FACT",
                                            "key", f.getKey() != null ? f.getKey() : ""
                                    )
                            )).toList();

                    qdrantVectorStore.add(docs); // embedding handled internally by Spring AI

                    pending.forEach(f -> {
                        f.setQdrantSyncStatus("INDEXED");
                        f.setQdrantPointId(UUID.randomUUID().toString());
                    });
                    log.info("[QdrantSync] Indexed {} facts for user={}", pending.size(), userEmail);
                    return ctx;
                }).subscribeOn(Schedulers.boundedElastic()))
                .flatMap(userContextRepository::save)
                .then();
    }

    /**
     * Embeds the session summary and upserts it to Qdrant.
     */
    public Mono<Void> syncSessionSummary(ChatSession session, String userEmail) {
        if (session == null || session.getSummary() == null || session.getSummary().isBlank()) {
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
            Document doc = new Document(
                    String.format("[Tóm tắt cuộc trò chuyện] %s", session.getSummary()),
                    Map.of(
                            "email", userEmail != null ? userEmail : "",
                            "type", "SUMMARY",
                            "sessionId", session.getId() != null ? session.getId() : ""
                    )
            );
            qdrantVectorStore.add(List.of(doc));
            log.info("[QdrantSync] Indexed summary for session={} user={}", session.getId(), userEmail);
            return null;
        }).subscribeOn(Schedulers.boundedElastic())
        .then(Mono.defer(() -> {
            session.setQdrantSyncStatus("INDEXED");
            return chatSessionRepository.save(session).then();
        }));
    }
}
