package com.project.familierapi.post.controller;

import com.project.familierapi.post.dto.CreatePostRequest;
import com.project.familierapi.post.dto.FeedResponse;
import com.project.familierapi.post.dto.PostDetailResponse;
import com.project.familierapi.post.dto.PostResponse;
import com.project.familierapi.post.dto.UpdatePostRequest;
import com.project.familierapi.post.service.ImageService;
import com.project.familierapi.post.service.PostService;
import com.project.familierapi.shared.dto.SuccessResponse;
import com.project.familierapi.user.domain.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final ImageService imageService;

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
            @RequestBody CreatePostRequest request) {
        PostResponse postResponse = postService.createPost(user, request);
        SuccessResponse<PostResponse> response = new SuccessResponse<>(
                "Post created successfully",
                postResponse
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload-image")
    public ResponseEntity<SuccessResponse<String>> uploadImage(
            @RequestParam("file") MultipartFile file) {
        String imageUrl = imageService.uploadImage(file);
        SuccessResponse<String> response = new SuccessResponse<>(
                "Image uploaded successfully",
                imageUrl
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload-images")
    public ResponseEntity<SuccessResponse<List<String>>> uploadImages(
            @RequestParam("files") MultipartFile[] files) {
        List<String> imageUrls = new ArrayList<>();
        for (MultipartFile file : files) {
            String imageUrl = imageService.uploadImage(file);
            imageUrls.add(imageUrl);
        }
        SuccessResponse<List<String>> response = new SuccessResponse<>(
                "Images uploaded successfully",
                imageUrls
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload-video")
    public ResponseEntity<SuccessResponse<String>> uploadVideo(
            @RequestParam("file") MultipartFile file) {
        String videoUrl = imageService.uploadVideo(file);
        SuccessResponse<String> response = new SuccessResponse<>(
                "Video uploaded successfully",
                videoUrl
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload-media")
    public ResponseEntity<SuccessResponse<String>> uploadMedia(
            @RequestParam("file") MultipartFile file) {
        String contentType = file.getContentType();
        String url;
        
        if (contentType != null && contentType.startsWith("video/")) {
            url = imageService.uploadVideo(file);
        } else {
            url = imageService.uploadImage(file);
        }
        
        SuccessResponse<String> response = new SuccessResponse<>(
                "Media uploaded successfully",
                url
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

    @DeleteMapping("/{postId}")
    public ResponseEntity<SuccessResponse<Void>> deletePost(
            @PathVariable Integer postId,
            @AuthenticationPrincipal User user) {
        postService.deletePost(postId, user);
        SuccessResponse<Void> response = new SuccessResponse<>(
                "Post deleted successfully",
                null
        );
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{postId}")
    public ResponseEntity<SuccessResponse<PostResponse>> updatePost(
            @PathVariable Integer postId,
            @AuthenticationPrincipal User user,
            @RequestBody UpdatePostRequest request) {
        PostResponse postResponse = postService.updatePost(postId, user, request);
        SuccessResponse<PostResponse> response = new SuccessResponse<>(
                "Post updated successfully",
                postResponse
        );
        return ResponseEntity.ok(response);
    }
}
