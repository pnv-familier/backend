package com.familier.ai.repository;

import com.familier.ai.entity.ChatMessage;
import com.familier.ai.entity.Sender;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface ChatMessageRepository extends ReactiveMongoRepository<ChatMessage, String> {
    Flux<ChatMessage> findAllBySessionIdOrderByTimestampAsc(String sessionId);
    Mono<Long> countBySender(Sender sender);
    Mono<Long> countBySenderAndTimestampBetween(Sender sender, Instant start, Instant end);
}
