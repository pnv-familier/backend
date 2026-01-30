package com.project.familierapi.auth.service;

import com.project.familierapi.auth.domain.User;
import com.project.familierapi.auth.repository.UserRepository;
import com.project.familierapi.auth.domain.AuthProvider;
import com.project.familierapi.auth.dto.RegisterRequestDto;
import com.project.familierapi.auth.dto.UserDto;
import com.project.familierapi.auth.exception.EmailAlreadyExistsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserDto register(RegisterRequestDto registerRequestDto) {
        userRepository.findByEmail(registerRequestDto.email()).ifPresent(user -> {
            throw new EmailAlreadyExistsException();
        });

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .fullName(registerRequestDto.fullName())
                .email(registerRequestDto.email())
                .passwordHash(passwordEncoder.encode(registerRequestDto.password()))
                .authProvider(AuthProvider.local)
                .isPremium(false)
                .build();

        User savedUser = userRepository.save(user);

        return new UserDto(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getAvatarUrl(),
                savedUser.getAuthProvider(),
                savedUser.isPremium(),
                savedUser.getCreatedAt(),
                savedUser.getUpdatedAt()
        );
    }
}
