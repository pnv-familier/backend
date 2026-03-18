package com.familier.ai.repository;

import com.familier.ai.entity.UserContext;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface UserContextRepository extends ReactiveMongoRepository<UserContext, String> {
    Mono<UserContext> findByEmail(String email);
}
