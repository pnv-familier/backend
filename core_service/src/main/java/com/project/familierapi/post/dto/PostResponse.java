package com.project.familierapi.post.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {
    private Integer postId;
    private AuthorInfo author;
    private String content;
    private LocalDateTime createdAt;
    private List<String> images;
    private List<String> videos;
    private Integer reactionCount;
    private Integer commentCount;
    private boolean hasMore;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthorInfo {
        private String userId;
        private String fullName;
        private String avatarUrl;
    }
}
