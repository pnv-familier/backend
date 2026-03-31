package com.project.familierapi.family.service;

import com.project.familierapi.family.domain.FamilyMember;
import com.project.familierapi.family.domain.Relationship;
import com.project.familierapi.family.domain.RelationshipInference;
import com.project.familierapi.family.repository.FamilyMemberRepository;
import com.project.familierapi.family.repository.RelationshipInferenceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
public class RelationshipInferenceService {
    private final RelationshipInferenceRepository relationshipInferenceRepository;
    private final FamilyMemberRepository familyMemberRepository;

    public RelationshipInferenceService(RelationshipInferenceRepository relationshipInferenceRepository,
            FamilyMemberRepository familyMemberRepository) {
        this.relationshipInferenceRepository = relationshipInferenceRepository;
        this.familyMemberRepository = familyMemberRepository;
    }

    @Transactional
    public void generateFamilyNetwork(String newUserId, String familyId, String adminId, Relationship relationToAdmin) {
        log.info("Starting relationship inference for user {} in family {}", newUserId, familyId);

        FamilyMember newMember = familyMemberRepository.findByUserId(newUserId)
                .orElseThrow(() -> new IllegalStateException("New user not found"));
        FamilyMember adminMember = familyMemberRepository.findByUserId(adminId)
                .orElseThrow(() -> new IllegalStateException("Admin not found"));

        String newUserEmail = newMember.getUser().getEmail();
        String adminEmail = adminMember.getUser().getEmail();
        String newUserGender = newMember.getUser().getGender() != null ? newMember.getUser().getGender().name() : "OTHER";
        String adminGender = adminMember.getUser().getGender() != null ? adminMember.getUser().getGender().name() : "OTHER";

        saveRelationship(familyId, newUserEmail, adminEmail, relationToAdmin);
        Relationship reverseRelation = getReverseRelationship(relationToAdmin, adminGender);
        if (reverseRelation != null) {
            saveRelationship(familyId, adminEmail, newUserEmail, reverseRelation);
            log.info("Saved bidirectional relationship: {} <-> {} (Admin)", relationToAdmin, reverseRelation);
        }

        List<FamilyMember> existingMembers = familyMemberRepository.findByFamilyIdOrderByJoinedAt(familyId);

        for (FamilyMember member : existingMembers) {
            String memberId = member.getUser().getId();
            String memberEmail = member.getUser().getEmail();
            String memberGender = member.getUser().getGender() != null ? member.getUser().getGender().name() : "OTHER";

            if (memberId.equals(newUserId) || memberId.equals(adminId)) {
                continue;
            }

            Relationship memberToAdmin = member.getRelationship();
            if (memberToAdmin == null) {
                log.warn("Member {} has no relationship to admin, skipping", memberId);
                continue;
            }

            Relationship zToY = inferRelationship(relationToAdmin, memberToAdmin, newUserGender, memberGender);
            if (zToY != null) {
                saveRelationship(familyId, newUserEmail, memberEmail, zToY);

                Relationship yToZ = getReverseRelationship(zToY, newUserGender);
                if (yToZ != null) {
                    saveRelationship(familyId, memberEmail, newUserEmail, yToZ);
                }
            }
        }
    }

    /**
     * Infer relationship between Z and Y based on their relationships to Admin (X)
     * 
     * @param zToAdmin Z's relationship to Admin
     * @param yToAdmin Y's relationship to Admin
     * @param zGender  Z's gender (MALE, FEMALE, OTHER)
     * @param yGender  Y's gender (MALE, FEMALE, OTHER)
     * @return Inferred relationship from Z to Y
     */
    public Relationship inferRelationship(Relationship zToAdmin, Relationship yToAdmin, String zGender,
            String yGender) {
        if (zToAdmin == null || yToAdmin == null) {
            return null;
        }

        // Z is child of X, Y is spouse of X -> Z is child of Y
        if ((zToAdmin == Relationship.SON || zToAdmin == Relationship.DAUGHTER) &&
                yToAdmin == Relationship.SPOUSE) {
            return zToAdmin; // Keep same gender
        }

        // Z is child of X, Y is child of X -> Z is sibling of Y (use gender to
        // determine BROTHER/SISTER)
        if ((zToAdmin == Relationship.SON || zToAdmin == Relationship.DAUGHTER) &&
                (yToAdmin == Relationship.SON || yToAdmin == Relationship.DAUGHTER)) {
            return "MALE".equals(yGender) ? Relationship.BROTHER : Relationship.SISTER;
        }

        // Z is sibling of X, Y is sibling of X -> Z is sibling of Y (use gender to
        // determine BROTHER/SISTER)
        if ((zToAdmin == Relationship.BROTHER || zToAdmin == Relationship.SISTER) &&
                (yToAdmin == Relationship.BROTHER || yToAdmin == Relationship.SISTER)) {
            return "MALE".equals(yGender) ? Relationship.BROTHER : Relationship.SISTER;
        }

        // Z is spouse of X, Y is child of X -> Z is parent of Y (use gender to
        // determine FATHER/MOTHER)
        if (zToAdmin == Relationship.SPOUSE &&
                (yToAdmin == Relationship.SON || yToAdmin == Relationship.DAUGHTER)) {
            return "MALE".equals(zGender) ? Relationship.FATHER : Relationship.MOTHER;
        }

        // Z is parent of X, Y is child of X -> Z is grandparent of Y
        if ((zToAdmin == Relationship.FATHER || zToAdmin == Relationship.MOTHER) &&
                (yToAdmin == Relationship.SON || yToAdmin == Relationship.DAUGHTER)) {
            return zToAdmin == Relationship.FATHER ? Relationship.GRANDFATHER : Relationship.GRANDMOTHER;
        }

        // Z is child of X, Y is parent of X -> Z is grandchild of Y
        if ((zToAdmin == Relationship.SON || zToAdmin == Relationship.DAUGHTER) &&
                (yToAdmin == Relationship.FATHER || yToAdmin == Relationship.MOTHER)) {
            return zToAdmin; // Grandchild keeps gender (SON/DAUGHTER)
        }

        // Z is sibling of X, Y is child of X -> Z is uncle/aunt of Y (use gender to
        // determine)
        if ((zToAdmin == Relationship.BROTHER || zToAdmin == Relationship.SISTER) &&
                (yToAdmin == Relationship.SON || yToAdmin == Relationship.DAUGHTER)) {
            return "MALE".equals(zGender) ? Relationship.BROTHER : Relationship.SISTER;
        }

        // Z is child of X, Y is sibling of X -> Z is nephew/niece of Y
        if ((zToAdmin == Relationship.SON || zToAdmin == Relationship.DAUGHTER) &&
                (yToAdmin == Relationship.BROTHER || yToAdmin == Relationship.SISTER)) {
            return zToAdmin; // Use SON/DAUGHTER as nephew/niece equivalent
        }

        log.debug("No inference rule for {} + {} combination", zToAdmin, yToAdmin);
        return null;
    }

    /**
     * Get reverse relationship based on the relationship type and gender
     */
    private Relationship getReverseRelationship(Relationship relationship, String userGender) {
        if (relationship == null)
            return null;

        boolean isMale = "MALE".equals(userGender);

        return switch (relationship) {
            case SPOUSE -> Relationship.SPOUSE;
            case SON -> isMale ? Relationship.FATHER : Relationship.MOTHER;
            case DAUGHTER -> isMale ? Relationship.FATHER : Relationship.MOTHER;
            case FATHER -> isMale ? Relationship.SON : Relationship.DAUGHTER;
            case MOTHER -> isMale ? Relationship.SON : Relationship.DAUGHTER;
            case BROTHER -> isMale ? Relationship.BROTHER : Relationship.SISTER;
            case SISTER -> isMale ? Relationship.BROTHER : Relationship.SISTER;
            case GRANDFATHER -> isMale ? Relationship.SON : Relationship.DAUGHTER;
            case GRANDMOTHER -> isMale ? Relationship.SON : Relationship.DAUGHTER;
        };
    }

    private void saveRelationship(String familyId, String user1Email, String user2Email, Relationship relationType) {
        // Check if relationship already exists
        if (relationshipInferenceRepository.findFirstByUser1EmailAndUser2Email(user1Email, user2Email).isPresent()) {
            log.debug("Relationship already exists: {} -> {}", user1Email, user2Email);
            return;
        }

        RelationshipInference inference = RelationshipInference.builder()
                .familyId(familyId)
                .user1Email(user1Email)
                .user2Email(user2Email)
                .relationType(relationType)
                .build();

        relationshipInferenceRepository.save(inference);
    }
}
