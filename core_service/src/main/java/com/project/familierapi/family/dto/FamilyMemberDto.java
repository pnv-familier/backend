package com.project.familierapi.family.dto;

import com.project.familierapi.family.domain.FamilyRole;
import java.time.LocalDateTime;

public record FamilyMemberDto(
        String userId,
        String displayName,
        String avatar,
        FamilyRole role,
        LocalDateTime joinedAt
) {
}