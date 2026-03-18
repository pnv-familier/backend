package com.familier.ai.config;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class MongoIndexInitializer {

    private final ReactiveMongoTemplate mongoTemplate;

    public MongoIndexInitializer(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void initializeIndexes() {
        createChatSessionIndexes();
        createUserContextIndexes();
    }

    private void createChatSessionIndexes() {
        mongoTemplate.indexOps("chat_sessions")
                .ensureIndex(new Index().on("status", org.springframework.data.domain.Sort.Direction.ASC)
                        .on("lastUpdate", org.springframework.data.domain.Sort.Direction.ASC))
                .subscribe();

        mongoTemplate.indexOps("chat_sessions")
                .ensureIndex(new Index().on("userEmail", org.springframework.data.domain.Sort.Direction.ASC)
                        .on("createdAt", org.springframework.data.domain.Sort.Direction.DESC))
                .subscribe();
    }

    private void createUserContextIndexes() {
        mongoTemplate.indexOps("user_contexts")
                .ensureIndex(new Index().on("email", org.springframework.data.domain.Sort.Direction.ASC))
                .subscribe();
    }
}
