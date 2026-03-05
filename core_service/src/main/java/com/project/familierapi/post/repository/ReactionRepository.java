package com.project.familierapi.post.repository;

import com.project.familierapi.post.domain.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReactionRepository extends JpaRepository<Reaction, Integer> {
    Optional<Reaction> findByPostPostIdAndUserId(Integer postId, String userId);
    int countByPostPostId(Integer postId);
}
