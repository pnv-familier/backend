package com.familier.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_sessions")
@CompoundIndexes({
    @CompoundIndex(name = "status_lastUpdate_idx", def = "{'status': 1, 'lastUpdate': 1}")
})
public class ChatSession {
    @Id
    private String id;
    private String userEmail; 
    private String targetContext;
    private LocalDateTime createdAt;
    private String summary;
    @Builder.Default
    private String status = "ACTIVE";
    private LocalDateTime lastUpdate;
    private LocalDateTime lastSummarizedAt;
}
