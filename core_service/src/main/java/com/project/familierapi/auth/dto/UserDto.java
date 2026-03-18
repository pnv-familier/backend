package com.project.familierapi.auth.dto;

import com.project.familierapi.auth.domain.AuthProvider;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class UserDto {
    private String id;
    private String email;
    private String fullName;
    private String avatarUrl;
    private AuthProvider authProvider;
    private boolean isPremium;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean isSetup;

    public UserDto(String id, String email, String fullName, String avatarUrl, AuthProvider authProvider,
                   boolean isPremium, LocalDateTime createdAt, LocalDateTime updatedAt, boolean isSetup) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.avatarUrl = avatarUrl;
        this.authProvider = authProvider;
        this.isPremium = isPremium;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isSetup = isSetup;
    }
}
