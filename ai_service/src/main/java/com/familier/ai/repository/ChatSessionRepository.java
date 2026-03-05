package com.familier.ai.repository;

import com.familier.ai.entity.ChatSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface ChatSessionRepository extends ReactiveMongoRepository<ChatSession, String> {
    Flux<ChatSession> findAllByUserEmailOrderByCreatedAtDesc(String userEmail, Pageable pageable);
    Flux<ChatSession> findAllByUserEmailOrderByCreatedAtDesc(String userEmail);
}
