package com.project.familierapi.family.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FamilyMemberListResponseDto(
        List<FamilyMemberDto> members,
        boolean shouldShowInvitePrompt,
        LocalDateTime familyCreatedAt
) {
}