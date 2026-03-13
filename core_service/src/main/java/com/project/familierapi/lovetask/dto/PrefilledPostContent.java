package com.project.familierapi.lovetask.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrefilledPostContent {
    private String content;
    private String senderName;
    private String taskTitle;
}