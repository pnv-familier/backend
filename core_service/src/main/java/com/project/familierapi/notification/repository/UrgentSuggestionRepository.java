package com.project.familierapi.notification.repository;

import com.project.familierapi.notification.domain.UrgentSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface UrgentSuggestionRepository extends JpaRepository<UrgentSuggestion, String> {

    List<UrgentSuggestion> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(String recipientId);

    // Kiểm tra rate limit: sender đã broadcast cùng subType trong khoảng thời gian chưa
    boolean existsBySenderEmailAndSubTypeAndCreatedAtAfter(String senderEmail, String subType, Instant after);
}
