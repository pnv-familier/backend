package com.familier.ai.entity;

import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@TypeAlias("TASK")
public class TaskPayload extends BasePayload {
    private String assigneeEmail;
    private String title;
    private String description;
    
}
