package com.project.familierapi.lovetask.dto;

import com.project.familierapi.lovetask.domain.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoveTaskResponse {
    private Integer taskId;
    private String title;
    private String description;
    private TaskStatus status;
    private UserInfo sender;
    private UserInfo assignee;
    private Integer sharedPostId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private Boolean canShare;
    private Boolean canComplete;
    private String reminderMessage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private String userId;
        private String fullName;
        private String avatarUrl;
    }
}