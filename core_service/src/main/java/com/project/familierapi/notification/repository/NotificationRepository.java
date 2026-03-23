package com.project.familierapi.notification.repository;

import com.project.familierapi.notification.domain.Notification;
import com.project.familierapi.notification.domain.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, String> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);
    List<Notification> findByRecipientIdAndTypeInOrderByCreatedAtDesc(String recipientId, List<NotificationType> types, Pageable pageable);
    long countByRecipientIdAndReadFalse(String recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipient.id = :userId AND n.read = false")
    void markAllReadByRecipientId(String userId);
}
