package com.familier.ai.repository;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import com.familier.ai.entity.Suggestion;

public interface SuggestionRepository extends ReactiveMongoRepository<Suggestion, String> {
    
}
