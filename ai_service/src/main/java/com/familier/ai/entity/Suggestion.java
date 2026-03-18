package com.familier.ai.entity;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Document(collection = "suggestions")
public class Suggestion {
    @Id
    private String id;
    private SuggestionType type;
    private String receiverEmail;
    private String title;
    private String description;

    private org.bson.Document payload;

    @Builder.Default
    private SuggestionStatus status = SuggestionStatus.PENDING;

    private Instant createdAt;
    private Instant expiredAt;

    private String triggerContext;
}
