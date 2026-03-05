package com.project.familierapi.post.controller;

import com.project.familierapi.post.domain.ReactionType;
import com.project.familierapi.post.dto.ReactionResponse;
import com.project.familierapi.post.dto.ToggleReactionRequest;
import com.project.familierapi.post.service.ReactionService;
import com.project.familierapi.shared.dto.SuccessResponse;
import com.project.familierapi.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/posts")
@RequiredArgsConstructor
public class ReactionController {
    private final ReactionService reactionService;

    @PostMapping("/{postId}/reactions")
    public ResponseEntity<SuccessResponse<ReactionResponse>> toggleReaction(
            @PathVariable Integer postId,
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String reactionType) {
        ReactionType type = reactionType != null 
                ? ReactionType.valueOf(reactionType.toUpperCase())
                : ReactionType.HEART;
        ReactionResponse response = reactionService.toggleReaction(postId, user, type);
        return ResponseEntity.ok(new SuccessResponse<>(
                response.isReacted() ? "Reaction added" : "Reaction removed",
                response
        ));
    }

    @GetMapping("/{postId}/reactions/status")
    public ResponseEntity<SuccessResponse<ReactionResponse>> getReactionStatus(
            @PathVariable Integer postId,
            @AuthenticationPrincipal User user) {
        ReactionResponse response = reactionService.getReactionStatus(postId, user);
        return ResponseEntity.ok(new SuccessResponse<>(
                "Reaction status retrieved",
                response
        ));
    }
}
