package com.project.familierapi.post.repository;

import com.project.familierapi.post.domain.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface ReactionRepository extends JpaRepository<Reaction, Integer> {
    Optional<Reaction> findByPostPostIdAndUserId(Integer postId, String userId);
    int countByPostPostId(Integer postId);
    
    @Query("SELECT r.post.postId as postId, COUNT(r) as count FROM Reaction r WHERE r.post.postId IN :postIds GROUP BY r.post.postId")
    List<Object[]> countByPostPostIdIn(@Param("postIds") List<Integer> postIds);

    List<Reaction> findByPostPostIdInAndUserId(List<Integer> postIds, String userId);
}
