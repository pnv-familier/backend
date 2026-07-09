package com.project.familierapi.notification.controller;

import com.project.familierapi.notification.dto.BroadcastRequest;
import com.project.familierapi.notification.dto.UrgentSuggestionResponse;
import com.project.familierapi.notification.service.UrgentSuggestionService;
import com.project.familierapi.shared.dto.SuccessResponse;
import com.project.familierapi.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/suggestions")
@RequiredArgsConstructor
public class UrgentSuggestionController {

    private final UrgentSuggestionService urgentSuggestionService;

    // Internal — chỉ AI service gọi qua X-Internal-Secret
    @PostMapping("/broadcast")
    public ResponseEntity<SuccessResponse<Void>> broadcast(
            @RequestBody BroadcastRequest request) {
        urgentSuggestionService.broadcast(request);
        return ResponseEntity.accepted()
                .body(new SuccessResponse<>("Broadcast accepted", null));
    }

    @GetMapping("/urgent")
    public ResponseEntity<SuccessResponse<List<UrgentSuggestionResponse>>> getUrgent(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new SuccessResponse<>("Urgent suggestions retrieved",
                urgentSuggestionService.getUnread(user.getId())));
    }

    @GetMapping("/urgent/{id}")
    public ResponseEntity<SuccessResponse<UrgentSuggestionResponse>> getById(
            @PathVariable String id,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new SuccessResponse<>("Urgent suggestion retrieved successfully",
                urgentSuggestionService.getById(id, user.getId())));
    }

    @PatchMapping("/urgent/{id}/read")
    public ResponseEntity<SuccessResponse<Void>> markAsRead(
            @PathVariable String id,
            @AuthenticationPrincipal User user) {
        urgentSuggestionService.markAsRead(id, user.getId());
        return ResponseEntity.ok(new SuccessResponse<>("Marked as read", null));
    }

    @PatchMapping("/urgent/read-all")
    public ResponseEntity<SuccessResponse<Void>> markAllAsRead(
            @AuthenticationPrincipal User user) {
        urgentSuggestionService.markAllAsRead(user.getId());
        return ResponseEntity.ok(new SuccessResponse<>("All marked as read", null));
    }
}
