package com.familier.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

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
    private Instant createdAt;
    private String summary;
    @Builder.Default
    private String status = "ACTIVE";
    private Instant lastUpdate;
    private Instant lastSummarizedAt;
    // "PENDING" = summary not yet embedded in Qdrant | "INDEXED" = successfully stored
    private String qdrantSyncStatus;
}
