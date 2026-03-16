package com.project.familierapi.lovetask.service;

import com.project.familierapi.family.domain.FamilyMember;
import com.project.familierapi.lovetask.domain.LoveTask;
import com.project.familierapi.lovetask.domain.TaskStatus;
import com.project.familierapi.lovetask.dto.*;
import com.project.familierapi.lovetask.exception.TaskNotFoundException;
import com.project.familierapi.lovetask.repository.LoveTaskRepository;
import com.project.familierapi.post.dto.CreatePostRequest;
import com.project.familierapi.post.service.PostService;
import com.project.familierapi.user.domain.User;
import com.project.familierapi.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LoveTaskService {
    private final LoveTaskRepository taskRepository;
    private final UserRepository userRepository;
    private final PostService postService;

    @Transactional
    public LoveTaskResponse createTask(User sender, CreateLoveTaskRequest request) {
        FamilyMember senderMember = sender.getFamily();
        if (senderMember == null) {
            throw new IllegalStateException("User is not part of any family");
        }

        User assignee = userRepository.findById(request.getAssigneeId())
                .orElseThrow(() -> new IllegalArgumentException("Assignee not found"));

        LoveTask task = LoveTask.builder()
                .family(senderMember.getFamily())
                .sender(sender)
                .assignee(assignee)
                .title(request.getTitle())
                .description(request.getDescription())
                .loveMessage(request.getLoveMessage())
                .status(TaskStatus.PENDING)
                .build();

        LoveTask savedTask = taskRepository.save(task);
        return mapToResponse(savedTask, assignee);
    }

    @Transactional(readOnly = true)
    public LoveTaskResponse getTaskDetail(User user, Integer taskId) {
        LoveTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + taskId));

        FamilyMember userMember = user.getFamily();
        if (userMember == null || !task.getFamily().getId().equals(userMember.getFamily().getId())) {
            throw new IllegalStateException("Task does not belong to user's family");
        }

        return mapToResponse(task, user);
    }

    @Transactional(readOnly = true)
    public PrefilledPostContent getPrefilledContent(User user, Integer taskId) {
        LoveTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + taskId));

        if (!task.getAssignee().getId().equals(user.getId())) {
            throw new IllegalStateException("Only assignee can get prefilled content");
        }

        String content = String.format("💕 I just completed a love task from %s: %s",
                task.getSender().getFullName(), task.getTitle());

        return PrefilledPostContent.builder()
                .content(content)
                .senderName(task.getSender().getFullName())
                .taskTitle(task.getTitle())
                .build();
    }

    @Transactional
    public LoveTaskResponse shareToFamilySpace(User user, Integer taskId, ShareTaskRequest request) {
        LoveTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + taskId));

        if (!task.getAssignee().getId().equals(user.getId())) {
            throw new IllegalStateException("Only assignee can share the task");
        }

        if (task.getStatus() != TaskStatus.PENDING) {
            throw new IllegalStateException("Task is already shared or completed");
        }

        // Get post content from request or use default prefilled content
        String postContent = (request != null && request.getPostContent() != null) 
                ? request.getPostContent()
                : String.format("💕 I just completed a love task from %s: %s",
                        task.getSender().getFullName(), task.getTitle());

        // Create post in family space
        List<String> imageUrls = request != null && request.getImageUrls() != null
                ? request.getImageUrls()
                : (request != null && request.getImageUrl() != null ? List.of(request.getImageUrl()) : null);

        CreatePostRequest postRequest = CreatePostRequest.builder()
                .content(postContent)
                .imageUrls(imageUrls)
                .build();

        var createdPost = postService.createPost(user, postRequest);

        // Update task status
        task.setStatus(TaskStatus.SHARED);
        task.setSharedPostId(createdPost.getPostId());
        LoveTask updatedTask = taskRepository.save(task);

        return mapToResponse(updatedTask, user);
    }

    @Transactional
    public LoveTaskResponse completeTask(User user, Integer taskId) {
        LoveTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with id: " + taskId));

        if (!task.getAssignee().getId().equals(user.getId())) {
            throw new IllegalStateException("Only assignee can complete the task");
        }

        if (task.getStatus() != TaskStatus.SHARED) {
            throw new IllegalStateException("Task must be shared before completing");
        }

        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        LoveTask updatedTask = taskRepository.save(task);

        return mapToResponse(updatedTask, user);
    }

    @Transactional(readOnly = true)
    public List<LoveTaskResponse> getMyTasks(User user) {
        List<LoveTask> tasks = taskRepository.findByAssigneeIdOrderByCreatedAtDesc(user.getId());
        return tasks.stream()
                .map(task -> mapToResponse(task, user))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LoveTaskResponse> getCreatedTasks(User user) {
        List<LoveTask> tasks = taskRepository.findBySenderIdOrderByCreatedAtDesc(user.getId());
        return tasks.stream()
                .map(task -> mapToResponse(task, user))
                .collect(Collectors.toList());
    }

    private LoveTaskResponse mapToResponse(LoveTask task, User currentUser) {
        boolean isAssignee = task.getAssignee().getId().equals(currentUser.getId());
        boolean canShare = isAssignee && task.getStatus() == TaskStatus.PENDING;
        boolean canComplete = isAssignee && task.getStatus() == TaskStatus.SHARED;

        String reminderMessage = null;
        if (isAssignee && task.getStatus() == TaskStatus.PENDING) {
            reminderMessage = "Share this task to the family space before completing it!";
        }

        return LoveTaskResponse.builder()
                .taskId(task.getTaskId())
                .title(task.getTitle())
                .description(task.getDescription())
                .loveMessage(task.getLoveMessage())
                .status(task.getStatus())
                .sender(LoveTaskResponse.UserInfo.builder()
                        .userId(task.getSender().getId())
                        .fullName(task.getSender().getFullName())
                        .avatarUrl(task.getSender().getAvatarUrl())
                        .build())
                .assignee(LoveTaskResponse.UserInfo.builder()
                        .userId(task.getAssignee().getId())
                        .fullName(task.getAssignee().getFullName())
                        .avatarUrl(task.getAssignee().getAvatarUrl())
                        .build())
                .sharedPostId(task.getSharedPostId())
                .createdAt(task.getCreatedAt())
                .completedAt(task.getCompletedAt())
                .canShare(canShare)
                .canComplete(canComplete)
                .reminderMessage(reminderMessage)
                .build();
    }
}