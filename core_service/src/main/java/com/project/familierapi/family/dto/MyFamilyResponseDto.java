package com.project.familierapi.family.dto;

import com.project.familierapi.family.domain.FamilyRole;

import java.time.LocalDateTime;

public record MyFamilyResponseDto(
        String id,
        String name,
        String inviteCode,
        String nickname,
        FamilyRole role,
        LocalDateTime joinedAt
) {
}
