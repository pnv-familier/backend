package com.project.familierapi.post.service;

import com.project.familierapi.family.domain.FamilyMember;
import com.project.familierapi.post.domain.Post;
import com.project.familierapi.post.domain.PostImage;
import com.project.familierapi.post.domain.PostVideo;
import com.project.familierapi.post.dto.CreatePostRequest;
import com.project.familierapi.post.dto.FeedResponse;
import com.project.familierapi.post.dto.PostDetailResponse;
import com.project.familierapi.post.dto.PostResponse;
import com.project.familierapi.post.dto.UpdatePostRequest;
import com.project.familierapi.post.exception.PostNotFoundException;
import com.project.familierapi.post.repository.PostRepository;
import com.project.familierapi.post.repository.ReactionRepository;
import com.project.familierapi.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class PostService {
    private static final int PREVIEW_LIMIT = 200;
    private final PostRepository postRepository;
    private final ReactionRepository reactionRepository;

    @Transactional(readOnly = true)
    public FeedResponse getHomeFeed(User user) {
        FamilyMember familyMember = user.getFamily();
        if (familyMember == null || familyMember.getFamily() == null) {
            return FeedResponse.builder()
                    .posts(List.of())
                    .isEmpty(true)
                    .build();
        }

        String familyId = familyMember.getFamily().getId();
        List<Post> posts = postRepository.findByFamilyIdOrderByCreatedAtDesc(familyId);

        List<PostResponse> postResponses = posts.stream()
                .map(post -> mapToPostResponse(post, user))
                .collect(Collectors.toList());

        return FeedResponse.builder()
                .posts(postResponses)
                .isEmpty(postResponses.isEmpty())
                .build();
    }

    private PostResponse mapToPostResponse(Post post, User currentUser) {
        String content = post.getContent();
        boolean hasMore = content != null && content.length() > PREVIEW_LIMIT;

        List<String> imageUrls = post.getImages() != null
                ? post.getImages().stream()
                    .sorted(Comparator.comparing(PostImage::getOrderIndex))
                    .map(PostImage::getImageUrl)
                    .collect(Collectors.toList())
                : List.of();

        List<String> videoUrls = post.getVideos() != null
                ? post.getVideos().stream()
                    .sorted(Comparator.comparing(PostVideo::getOrderIndex))
                    .map(PostVideo::getVideoUrl)
                    .collect(Collectors.toList())
                : List.of();

        int reactionCount = reactionRepository.countByPostPostId(post.getPostId());
        int commentCount = post.getComments() != null ? post.getComments().size() : 0;

        var userReaction = reactionRepository.findByPostPostIdAndUserId(post.getPostId(), currentUser.getId());

        return PostResponse.builder()
                .postId(post.getPostId())
                .author(PostResponse.AuthorInfo.builder()
                        .userId(post.getUser().getId())
                        .fullName(post.getUser().getFullName())
                        .avatarUrl(post.getUser().getAvatarUrl())
                        .build())
                .content(content)
                .createdAt(post.getCreatedAt())
                .images(imageUrls)
                .videos(videoUrls)
                .reactionCount(reactionCount)
                .commentCount(commentCount)
                .hasMore(hasMore)
                .userReacted(userReaction.isPresent())
                .reactionType(userReaction.map(r -> r.getReactionType().name()).orElse(null))
                .build();
    }

    @Transactional
    public void deletePost(Integer postId, User user) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found with id: " + postId));

        if (!post.getUser().getId().equals(user.getId())) {
            throw new com.project.familierapi.post.exception.UnauthorizedPostAccessException(
                "You don't have permission to delete this post");
        }

        postRepository.delete(post);
    }

    @Transactional
    public PostResponse updatePost(Integer postId, User user, UpdatePostRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found with id: " + postId));

        if (!post.getUser().getId().equals(user.getId())) {
            throw new com.project.familierapi.post.exception.UnauthorizedPostAccessException(
                "You don't have permission to update this post");
        }

        if ((request.getContent() == null || request.getContent().trim().isEmpty()) &&
            (request.getImageUrls() == null || request.getImageUrls().isEmpty()) &&
            (request.getVideoUrls() == null || request.getVideoUrls().isEmpty())) {
            throw new IllegalArgumentException("Content, images or videos required");
        }

        post.setContent(request.getContent());

        if (request.getImageUrls() != null) {
            final Post finalPost = post;
            List<PostImage> images = IntStream.range(0, request.getImageUrls().size())
                    .mapToObj(i -> PostImage.builder()
                            .post(finalPost)
                            .imageUrl(request.getImageUrls().get(i))
                            .orderIndex(i)
                            .build())
                    .collect(Collectors.toList());
            post.setImages(images);
        }

        if (request.getVideoUrls() != null) {
            final Post finalPost = post;
            List<PostVideo> videos = IntStream.range(0, request.getVideoUrls().size())
                    .mapToObj(i -> PostVideo.builder()
                            .post(finalPost)
                            .videoUrl(request.getVideoUrls().get(i))
                            .orderIndex(i)
                            .build())
                    .collect(Collectors.toList());
            post.setVideos(videos);
        }

        post = postRepository.save(post);
        return mapToPostResponse(post, user);
    }

    @Transactional
    public PostResponse createPost(User user, CreatePostRequest request) {
        FamilyMember familyMember = user.getFamily();
        
        if (familyMember == null || familyMember.getFamily() == null) {
            throw new IllegalStateException("User must belong to a family to create posts. Please create or join a family first.");
        }

        if ((request.getContent() == null || request.getContent().trim().isEmpty()) &&
            (request.getImageUrls() == null || request.getImageUrls().isEmpty()) &&
            (request.getVideoUrls() == null || request.getVideoUrls().isEmpty())) {
            throw new IllegalArgumentException("Content, images or videos required");
        }

        Post post = Post.builder()
                .family(familyMember.getFamily())
                .user(user)
                .content(request.getContent())
                .build();

        post = postRepository.save(post);

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            final Post finalPost = post;
            List<PostImage> images = IntStream.range(0, request.getImageUrls().size())
                    .mapToObj(i -> PostImage.builder()
                            .post(finalPost)
                            .imageUrl(request.getImageUrls().get(i))
                            .orderIndex(i)
                            .build())
                    .collect(Collectors.toList());
            post.setImages(images);
            post = postRepository.save(post);
        }

        if (request.getVideoUrls() != null && !request.getVideoUrls().isEmpty()) {
            final Post finalPost = post;
            List<PostVideo> videos = IntStream.range(0, request.getVideoUrls().size())
                    .mapToObj(i -> PostVideo.builder()
                            .post(finalPost)
                            .videoUrl(request.getVideoUrls().get(i))
                            .orderIndex(i)
                            .build())
                    .collect(Collectors.toList());
            post.setVideos(videos);
            post = postRepository.save(post);
        }

        return mapToPostResponse(post, user);
    }

    @Transactional(readOnly = true)
    public PostDetailResponse getPostDetail(Integer postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found with id: " + postId));

        List<String> imageUrls = post.getImages() != null
                ? post.getImages().stream()
                    .sorted(Comparator.comparing(PostImage::getOrderIndex))
                    .map(PostImage::getImageUrl)
                    .collect(Collectors.toList())
                : List.of();

        List<String> videoUrls = post.getVideos() != null
                ? post.getVideos().stream()
                    .sorted(Comparator.comparing(PostVideo::getOrderIndex))
                    .map(PostVideo::getVideoUrl)
                    .collect(Collectors.toList())
                : List.of();

        int reactionCount = post.getReactions() != null ? post.getReactions().size() : 0;
        int commentCount = post.getComments() != null ? post.getComments().size() : 0;

        return PostDetailResponse.builder()
                .postId(post.getPostId())
                .author(PostResponse.AuthorInfo.builder()
                        .userId(post.getUser().getId())
                        .fullName(post.getUser().getFullName())
                        .avatarUrl(post.getUser().getAvatarUrl())
                        .build())
                .fullContent(post.getContent())
                .createdAt(post.getCreatedAt())
                .images(imageUrls)
                .videos(videoUrls)
                .reactionCount(reactionCount)
                .commentCount(commentCount)
                .build();
    }
}
