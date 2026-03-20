package com.project.familierapi.family.repository;

import com.project.familierapi.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.project.familierapi.family.domain.Family;

import java.time.LocalDateTime;
import java.util.Optional;

public interface FamilyRepository extends JpaRepository<Family, String> {

    boolean existsByInviteCode(String inviteCode);

    boolean existsByUser(User user);


    Optional<Family> findByInviteCode(String inviteCode);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT f FROM Family f WHERE " +
           "(:keyword IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(f.user.email) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Family> searchFamilies(@Param("keyword") String keyword, Pageable pageable);
}
