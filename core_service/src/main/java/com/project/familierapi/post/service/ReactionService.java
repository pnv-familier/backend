package com.project.familierapi.post.service;

import com.project.familierapi.notification.domain.NotificationType;
import com.project.familierapi.notification.service.NotificationService;
import com.project.familierapi.post.domain.Post;
import com.project.familierapi.post.domain.Reaction;
import com.project.familierapi.post.domain.ReactionType;
import com.project.familierapi.post.dto.ReactionResponse;
import com.project.familierapi.post.exception.PostNotFoundException;
import com.project.familierapi.post.repository.PostRepository;
import com.project.familierapi.post.repository.ReactionRepository;
import com.project.familierapi.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReactionService {
    private final ReactionRepository reactionRepository;
    private final PostRepository postRepository;
    private final NotificationService notificationService;

    @Transactional
    public ReactionResponse toggleReaction(Integer postId, User user, ReactionType reactionType) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found with id: " + postId));

        var existingReaction = reactionRepository.findByPostPostIdAndUserId(postId, user.getId());

        if (existingReaction.isPresent()) {
            reactionRepository.delete(existingReaction.get());
            int count = reactionRepository.countByPostPostId(postId);
            return ReactionResponse.builder()
                    .postId(postId)
                    .reacted(false)
                    .reactionCount(count)
                    .build();
        } else {
            Reaction reaction = Reaction.builder()
                    .post(post)
                    .user(user)
                    .reactionType(reactionType)
                    .build();
            reactionRepository.save(reaction);
            int count = reactionRepository.countByPostPostId(postId);
            // AC-NT-03: notify post owner
            String postBody = post.getContent() != null && post.getContent().length() > 80
                    ? post.getContent().substring(0, 80) : post.getContent();
            notificationService.createAndPush(
                    post.getUser(), user, NotificationType.POST_REACTION,
                    user.getFullName() + " reacted to your post ❤️",
                    postBody != null ? postBody : "",
                    String.valueOf(postId));
            return ReactionResponse.builder()
                    .postId(postId)
                    .reacted(true)
                    .reactionCount(count)
                    .reactionType(reactionType)
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public ReactionResponse getReactionStatus(Integer postId, User user) {
        var reaction = reactionRepository.findByPostPostIdAndUserId(postId, user.getId());
        int count = reactionRepository.countByPostPostId(postId);
        
        if (reaction.isPresent()) {
            return ReactionResponse.builder()
                    .postId(postId)
                    .reacted(true)
                    .reactionCount(count)
                    .reactionType(reaction.get().getReactionType())
                    .build();
        } else {
            return ReactionResponse.builder()
                    .postId(postId)
                    .reacted(false)
                    .reactionCount(count)
                    .build();
        }
    }
}
