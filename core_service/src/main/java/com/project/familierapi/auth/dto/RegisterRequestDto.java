package com.project.familierapi.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.Length;

public record RegisterRequestDto(
    @NotBlank
    @Length(min = 2, max = 50, message = "Must be 2 - 50 characters")
    String fullName,
    @NotBlank
    @Email
    String email,
    @NotBlank
    String password
) {}
