package com.familier.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_contexts")
public class UserContext {
    @Id
    private String id;
    
    @Indexed
    private String email;
    
    private String globalContext;
    
    @Builder.Default
    private List<Fact> facts = new ArrayList<>();
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Fact {
        private String key;
        private String value;
        private Double confidence;
        private String sourceSessionId;
        private Instant updatedAt;
    }
}
