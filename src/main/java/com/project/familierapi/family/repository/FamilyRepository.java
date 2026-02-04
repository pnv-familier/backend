package com.project.familierapi.family.repository;

import com.project.familierapi.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import com.project.familierapi.family.domain.Family;

import java.util.Optional;

public interface FamilyRepository extends JpaRepository<Family, String> {

    boolean existsByInviteCode(String inviteCode);

    boolean existsByUser(User user);
    
    Optional<Family> findByInviteCode(String inviteCode);
}
