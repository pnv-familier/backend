package com.project.familierapi.user.service;

import com.project.familierapi.auth.dto.UserDto;
import com.project.familierapi.user.domain.User;
import com.project.familierapi.user.dto.UpdateProfileRequest;
import com.project.familierapi.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserDto getCurrentUser(User user) {
        return toUserDto(user);
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public UserDto updateProfile(User currentUser, UpdateProfileRequest request) {
        Map<String, Object> existingProfile = currentUser.getProfile();
        if (existingProfile == null) {
            existingProfile = new HashMap<>();
        }

        if (request.getProfile() != null) {
            existingProfile.putAll(request.getProfile());
        }

        currentUser.setProfile(existingProfile);
        currentUser.setSetup(true);
        userRepository.save(currentUser);

        return toUserDto(currentUser);
    }

    private UserDto toUserDto(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getAuthProvider(),
                user.isPremium(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.isSetup()
        );
    }
}
