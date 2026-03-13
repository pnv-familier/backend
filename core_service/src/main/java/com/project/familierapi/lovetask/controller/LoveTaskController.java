package com.project.familierapi.lovetask.controller;

import com.project.familierapi.lovetask.dto.*;
import com.project.familierapi.lovetask.service.LoveTaskService;
import com.project.familierapi.shared.dto.SuccessResponse;
import com.project.familierapi.user.domain.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/love-tasks", "/api/love-tasks", "/love-tasks"})
@RequiredArgsConstructor
public class LoveTaskController {
    private final LoveTaskService taskService;

    @PostMapping
    public ResponseEntity<SuccessResponse<LoveTaskResponse>> createTask(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateLoveTaskRequest request) {
        LoveTaskResponse response = taskService.createTask(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SuccessResponse<>("Love task created successfully", response));
    }

    @GetMapping("/my-tasks")
    public ResponseEntity<SuccessResponse<List<LoveTaskResponse>>> getMyTasks(
            @AuthenticationPrincipal User user) {
        List<LoveTaskResponse> response = taskService.getMyTasks(user);
        return ResponseEntity.ok(new SuccessResponse<>("My tasks retrieved", response));
    }

    @GetMapping("/received")
    public ResponseEntity<SuccessResponse<List<LoveTaskResponse>>> getReceivedTasks(
            @AuthenticationPrincipal User user) {
        List<LoveTaskResponse> response = taskService.getMyTasks(user);
        return ResponseEntity.ok(new SuccessResponse<>("Received tasks retrieved", response));
    }

    @GetMapping("/created")
    public ResponseEntity<SuccessResponse<List<LoveTaskResponse>>> getCreatedTasks(
            @AuthenticationPrincipal User user) {
        List<LoveTaskResponse> response = taskService.getCreatedTasks(user);
        return ResponseEntity.ok(new SuccessResponse<>("Created tasks retrieved", response));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<SuccessResponse<LoveTaskResponse>> getTaskDetail(
            @AuthenticationPrincipal User user,
            @PathVariable Integer taskId) {
        LoveTaskResponse response = taskService.getTaskDetail(user, taskId);
        return ResponseEntity.ok(new SuccessResponse<>("Task details retrieved", response));
    }

    @GetMapping("/{taskId}/prefilled-content")
    public ResponseEntity<SuccessResponse<PrefilledPostContent>> getPrefilledContent(
            @AuthenticationPrincipal User user,
            @PathVariable Integer taskId) {
        PrefilledPostContent response = taskService.getPrefilledContent(user, taskId);
        return ResponseEntity.ok(new SuccessResponse<>("Prefilled content retrieved", response));
    }

    @PostMapping("/{taskId}/share")
    public ResponseEntity<SuccessResponse<LoveTaskResponse>> shareToFamilySpace(
            @AuthenticationPrincipal User user,
            @PathVariable Integer taskId,
            @RequestBody(required = false) ShareTaskRequest request) {
        LoveTaskResponse response = taskService.shareToFamilySpace(user, taskId, request);
        return ResponseEntity.ok(new SuccessResponse<>("Task shared to family space successfully", response));
    }

    @PostMapping("/{taskId}/complete")
    public ResponseEntity<SuccessResponse<LoveTaskResponse>> completeTask(
            @AuthenticationPrincipal User user,
            @PathVariable Integer taskId) {
        LoveTaskResponse response = taskService.completeTask(user, taskId);
        return ResponseEntity.ok(new SuccessResponse<>("Love task completed successfully", response));
    }
}
