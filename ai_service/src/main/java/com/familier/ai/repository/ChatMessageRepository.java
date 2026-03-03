package com.familier.ai.repository;

import com.familier.ai.entity.ChatMessage;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface ChatMessageRepository extends ReactiveMongoRepository<ChatMessage, String> {
    Flux<ChatMessage> findAllBySessionIdOrderByTimestampAsc(String sessionId);
}
