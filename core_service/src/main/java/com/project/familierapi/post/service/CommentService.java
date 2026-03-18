package com.project.familierapi.post.service;

import com.project.familierapi.post.domain.Comment;
import com.project.familierapi.post.domain.Post;
import com.project.familierapi.post.dto.CommentListResponse;
import com.project.familierapi.post.dto.CommentResponse;
import com.project.familierapi.post.dto.CreateCommentRequest;
import com.project.familierapi.post.exception.PostNotFoundException;
import com.project.familierapi.post.repository.CommentRepository;
import com.project.familierapi.post.repository.PostRepository;
import com.project.familierapi.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    @Transactional(readOnly = true)
    public CommentListResponse getComments(Integer postId, int page, int size) {
        if (!postRepository.existsById(postId)) {
            throw new PostNotFoundException("Post not found with id: " + postId);
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Comment> commentPage = commentRepository.findByPostIdAndParentIsNull(postId, pageable);

        List<CommentResponse> comments = commentPage.getContent().stream()
                .map(this::mapToCommentResponse)
                .collect(Collectors.toList());

        return CommentListResponse.builder()
                .comments(comments)
                .totalComments((int) commentPage.getTotalElements())
                .currentPage(page)
                .totalPages(commentPage.getTotalPages())
                .hasMore(commentPage.hasNext())
                .build();
    }

    @Transactional
    public CommentResponse createComment(Integer postId, User user, CreateCommentRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found with id: " + postId));

        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .content(request.getContent())
                .build();

        comment = commentRepository.save(comment);
        return mapToCommentResponse(comment);
    }

    private CommentResponse mapToCommentResponse(Comment comment) {
        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .author(CommentResponse.AuthorInfo.builder()
                        .userId(comment.getUser().getId())
                        .fullName(comment.getUser().getFullName())
                        .avatarUrl(comment.getUser().getAvatarUrl())
                        .build())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
