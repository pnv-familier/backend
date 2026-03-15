package com.familier.ai.entity;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = EventPayload.class, name = "EVENT"),
        @JsonSubTypes.Type(value = TaskPayload.class, name = "TASK"),
        @JsonSubTypes.Type(value = OfflineSuggestionPayload.class, name = "OFFLINE")

})
public abstract class BasePayload {
    
}
