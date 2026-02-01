package com.project.familierapi.family.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.familierapi.family.domain.Family;

public interface FamilyRepository extends JpaRepository<Family, String> {

    boolean existsByInviteCode(String inviteCode);
}
