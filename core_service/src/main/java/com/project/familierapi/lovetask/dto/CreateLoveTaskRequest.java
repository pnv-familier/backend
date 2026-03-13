package com.project.familierapi.lovetask.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateLoveTaskRequest {
    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private String assigneeId;

    private String loveMessage;

    @JsonProperty("assignedToUserId")
    public void setAssignedToUserId(String assignedToUserId) {
        this.assigneeId = assignedToUserId;
    }
}