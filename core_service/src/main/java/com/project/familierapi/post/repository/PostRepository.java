package com.project.familierapi.post.repository;

import com.project.familierapi.post.domain.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Integer> {
    @Query("SELECT p FROM Post p WHERE p.family.id = :familyId ORDER BY p.createdAt DESC")
    List<Post> findByFamilyIdOrderByCreatedAtDesc(@Param("familyId") String familyId);
}
