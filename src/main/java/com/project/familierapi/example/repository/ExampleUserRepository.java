package com.project.familierapi.example.repository;

import com.project.familierapi.example.domain.ExampleUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExampleUserRepository extends JpaRepository<ExampleUser, String> {
    Optional<ExampleUser> findByEmail(String email);
}
