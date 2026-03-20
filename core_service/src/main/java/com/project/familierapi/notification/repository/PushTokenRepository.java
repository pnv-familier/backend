package com.project.familierapi.notification.repository;

import com.project.familierapi.notification.domain.PushToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushTokenRepository extends JpaRepository<PushToken, String> {
    List<PushToken> findByUserId(String userId);
    Optional<PushToken> findByToken(String token);
    void deleteByToken(String token);
}
