package com.project.familierapi.post.repository;

import com.project.familierapi.post.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Integer> {
    @Query("SELECT c FROM Comment c WHERE c.post.postId = :postId AND c.parentComment IS NULL ORDER BY c.createdAt ASC")
    Page<Comment> findByPostIdAndParentIsNull(@Param("postId") Integer postId, Pageable pageable);
    
    int countByPostPostId(Integer postId);
}
