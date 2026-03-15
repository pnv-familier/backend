package com.familier.ai.dto;

import com.familier.ai.entity.Sender;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class ChatMessageDto {
    private String id;
    private String content;
    private List<String> suggestions;  // Added per spec: return suggestions array
    private Instant timestamp;
    private Boolean isAi;

    public static ChatMessageDto fromEntity(com.familier.ai.entity.ChatMessage entity) {
        return ChatMessageDto.builder()
                .id(entity.getId())
                .content(entity.getContent())
                .suggestions(entity.getSuggestions())
                .timestamp(entity.getTimestamp())
                .isAi(entity.getSender() == Sender.AI)
                .build();
    }
}
