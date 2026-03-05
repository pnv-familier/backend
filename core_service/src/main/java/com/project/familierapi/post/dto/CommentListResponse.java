package com.project.familierapi.post.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentListResponse {
    private List<CommentResponse> comments;
    private int totalComments;
    private int currentPage;
    private int totalPages;
    private boolean hasMore;
}
