package com.familier.ai.dto;

import com.familier.ai.entity.Sender;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageDto {
    private String id;
    private String content;
    private LocalDateTime timestamp;
    private Boolean isAi;

    public static ChatMessageDto fromEntity(com.familier.ai.entity.ChatMessage entity) {
        return ChatMessageDto.builder()
                .id(entity.getId())
                .content(entity.getContent())
                .timestamp(entity.getTimestamp())
                .isAi(entity.getSender() == Sender.AI)
                .build();
    }
}
