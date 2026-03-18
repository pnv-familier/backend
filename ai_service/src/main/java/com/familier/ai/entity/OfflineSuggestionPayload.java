package com.familier.ai.entity;

import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
public class OfflineSuggestionPayload extends BasePayload {
    private String action;
}
