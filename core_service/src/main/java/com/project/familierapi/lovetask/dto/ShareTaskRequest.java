package com.project.familierapi.lovetask.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareTaskRequest {
    private String postContent;
    private String imageUrl;
}