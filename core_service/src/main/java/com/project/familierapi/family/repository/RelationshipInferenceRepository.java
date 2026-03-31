package com.project.familierapi.family.repository;

import com.project.familierapi.family.domain.Relationship;
import com.project.familierapi.family.domain.RelationshipInference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RelationshipInferenceRepository extends JpaRepository<RelationshipInference, Long> {
    List<RelationshipInference> findByFamilyId(String familyId);

    Optional<RelationshipInference> findFirstByUser1EmailAndUser2Email(String user1Email, String user2Email);

    List<RelationshipInference> findByUser1Email(String user1Email);

    Optional<RelationshipInference> findFirstByUser1EmailAndRelationType(String user1Email, Relationship relationType);

    Optional<RelationshipInference> findFirstByUser2EmailAndRelationType(String user2Email, Relationship relationType);
}
