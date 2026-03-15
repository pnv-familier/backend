package com.familier.ai.entity;

import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@TypeAlias("OFFLINE")
public class OfflineSuggestionPayload extends BasePayload {
    private String action;
}
