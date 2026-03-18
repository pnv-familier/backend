package com.project.familierapi.family.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "relationship_inferences", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"user1_email", "user2_email"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RelationshipInference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_id", nullable = false)
    private String familyId;

    @Column(name = "user1_email", nullable = false)
    private String user1Email;

    @Column(name = "user2_email", nullable = false)
    private String user2Email;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type")
    private Relationship relationType;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
