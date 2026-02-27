package com.project.familierapi.family.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateFamilyRequestDto(
    @NotBlank(message = "Family name cannot be blank")
    @Size(min = 2, max = 30, message = "Family name must be between 2 and 30 characters")
    @Pattern(regexp = ".*[a-zA-Z].*", message = "Family name must contain at least one letter")
    String name
) {
    public CreateFamilyRequestDto {
        name = name != null ? name.trim() : null;
    }
}
