package com.familier.ai.entity;

import lombok.Data;

@Data
public class TaskPayload extends BasePayload {
    private String assigneeEmail;
    private String title;
    private String description;
    
}
