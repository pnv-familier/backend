package com.project.familierapi.auth.repository;

import com.project.familierapi.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
