package com.familier.ai.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.familier.ai.entity.Suggestion;
import com.familier.ai.entity.SuggestionStatus;
import reactor.core.publisher.Flux;

public interface SuggestionRepository extends ReactiveMongoRepository<Suggestion, String> {
    Flux<Suggestion> findByReceiverEmailOrderByCreatedAtDesc(String receiverEmail);
    Flux<Suggestion> findByReceiverEmailAndStatusOrderByCreatedAtDesc(String receiverEmail, SuggestionStatus status);
}
