package com.project.familierapi.family.dto;

import java.time.LocalDateTime;

public record FamilyResponseDto
    (String id, String name, String inviteCode, LocalDateTime createdAt) {

}
