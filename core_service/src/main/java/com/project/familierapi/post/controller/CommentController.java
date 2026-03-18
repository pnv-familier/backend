package com.project.familierapi.post.controller;

import com.project.familierapi.post.dto.CommentListResponse;
import com.project.familierapi.post.dto.CommentResponse;
import com.project.familierapi.post.dto.CreateCommentRequest;
import com.project.familierapi.post.service.CommentService;
import com.project.familierapi.shared.dto.SuccessResponse;
import com.project.familierapi.user.domain.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/posts")
@RequiredArgsConstructor
public class CommentController {
    private final CommentService commentService;

    @GetMapping("/{postId}/comments")
    public ResponseEntity<SuccessResponse<CommentListResponse>> getComments(
            @PathVariable Integer postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CommentListResponse response = commentService.getComments(postId, page, size);
        return ResponseEntity.ok(new SuccessResponse<>(
                "Comments retrieved successfully",
                response
        ));
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<SuccessResponse<CommentResponse>> createComment(
            @PathVariable Integer postId,
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateCommentRequest request) {
        CommentResponse response = commentService.createComment(postId, user, request);
        return ResponseEntity.ok(new SuccessResponse<>(
                "Comment posted successfully",
                response
        ));
    }
}
