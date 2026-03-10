package com.project.familierapi.family.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinFamilyRequestDto(
    @NotBlank(message = "Join code is required")
    String joinCode,
    @NotBlank(message = "Relationship is required")
    String relationship
) {}