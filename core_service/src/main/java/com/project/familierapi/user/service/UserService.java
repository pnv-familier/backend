package com.project.familierapi.user.service;

import com.project.familierapi.auth.dto.UserDto;
import com.project.familierapi.user.domain.Gender;
import com.project.familierapi.user.domain.User;
import com.project.familierapi.user.dto.UpdateProfileRequest;
import com.project.familierapi.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public UserDto getCurrentUser(User user) {
        return toUserDto(user);
    }

    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public UserDto updateProfile(User currentUser, UpdateProfileRequest request) {
        if (request.getDateOfBirth() != null) {
            LocalDate dob = parseDateOfBirth(request.getDateOfBirth());
            validateDateOfBirth(dob);
            currentUser.setDateOfBirth(dob.atStartOfDay());
        }

        if (request.getGender() != null) {
            try {
                Gender gender = Gender.valueOf(request.getGender().toUpperCase());
                currentUser.setGender(gender);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid gender value. Must be MALE, FEMALE, or OTHER");
            }
        }

        if (request.getHobbies() != null) {
            if (request.getHobbies().size() > 5) {
                throw new IllegalArgumentException("Maximum 5 hobbies allowed");
            }
            currentUser.setHobbies(request.getHobbies());
        }

        currentUser.setSetup(true);
        userRepository.save(currentUser);

        return toUserDto(currentUser);
    }

    private LocalDate parseDateOfBirth(String dateString) {
        try {
            return LocalDate.parse(dateString, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Use YYYY-MM-DD");
        }
    }

    private void validateDateOfBirth(LocalDate dob) {
        LocalDate now = LocalDate.now();
        if (dob.isAfter(now)) {
            throw new IllegalArgumentException("Date of birth must be in the past");
        }
        if (dob.isAfter(now.minusYears(1))) {
            throw new IllegalArgumentException("User must be at least 1 year old");
        }
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
