package com.project.familierapi.family.service;

import com.project.familierapi.family.domain.Relationship;
import com.project.familierapi.family.repository.RelationshipInferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RelationshipMappingService {

    private final RelationshipInferenceRepository relationshipInferenceRepository;

    /**
     * Map relationship type to actual user email
     * @param currentUserEmail Email of current user
     * @param relationTypeStr Relationship type as string (e.g., "FATHER", "MOTHER")
     * @return Email of the target user, or empty if not found
     */
    public Optional<String> mapRelationToEmail(String currentUserEmail, String relationTypeStr) {
        try {
            Relationship relationType = Relationship.valueOf(relationTypeStr.toUpperCase());
            
            return relationshipInferenceRepository
                    .findByUser1EmailAndRelationType(currentUserEmail, relationType)
                    .map(inference -> {
                        log.debug("Mapped {} for user {} to email {}", 
                                relationType, currentUserEmail, inference.getUser2Email());
                        return inference.getUser2Email();
                    });
        } catch (IllegalArgumentException e) {
            log.warn("Invalid relationship type: {}", relationTypeStr);
            return Optional.empty();
        }
    }
}
