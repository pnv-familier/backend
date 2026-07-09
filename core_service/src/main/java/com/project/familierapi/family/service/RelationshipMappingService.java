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

    public Optional<String> mapRelationToEmail(String currentUserEmail, String relationTypeStr) {
        try {
            Relationship relationType = Relationship.valueOf(relationTypeStr.toUpperCase());

            return relationshipInferenceRepository
                    .findFirstByUser2EmailAndRelationType(currentUserEmail, relationType)
                    .map(inference -> inference.getUser1Email());

        } catch (IllegalArgumentException e) {
            log.warn("Invalid relationship type: {}", relationTypeStr);
            return Optional.empty();
        }
    }
}
