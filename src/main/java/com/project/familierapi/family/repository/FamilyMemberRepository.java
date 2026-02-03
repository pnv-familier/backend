package com.project.familierapi.family.repository;

import com.project.familierapi.family.domain.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {
    Optional<FamilyMember> findByUserId(String userId);
}
