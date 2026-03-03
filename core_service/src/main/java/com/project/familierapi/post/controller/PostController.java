package com.project.familierapi.post.controller;

import com.project.familierapi.post.dto.CreatePostRequest;
import com.project.familierapi.post.dto.FeedResponse;
import com.project.familierapi.post.dto.PostDetailResponse;
import com.project.familierapi.post.dto.PostResponse;
import com.project.familierapi.post.service.PostService;
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
public class PostController {

    private final PostService postService;

    @GetMapping("/feed")
    public ResponseEntity<SuccessResponse<FeedResponse>> getHomeFeed(
            @AuthenticationPrincipal User user) {
        FeedResponse feedResponse = postService.getHomeFeed(user);
        SuccessResponse<FeedResponse> response = new SuccessResponse<>(
                "Feed retrieved successfully", 
                feedResponse
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<SuccessResponse<PostResponse>> createPost(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreatePostRequest request) {
        PostResponse postResponse = postService.createPost(user, request);
        SuccessResponse<PostResponse> response = new SuccessResponse<>(
                "Post created successfully",
                postResponse
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{postId}")
    public ResponseEntity<SuccessResponse<PostDetailResponse>> getPostDetail(
            @PathVariable Integer postId) {
        PostDetailResponse postDetail = postService.getPostDetail(postId);
        SuccessResponse<PostDetailResponse> response = new SuccessResponse<>(
                "Post detail retrieved successfully",
                postDetail
        );
        return ResponseEntity.ok(response);
    }
}
