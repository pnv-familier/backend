package com.project.familierapi.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.project.familierapi.auth.domain.AuthProvider;
import com.project.familierapi.user.domain.Role;
import com.project.familierapi.user.domain.User;
import com.project.familierapi.auth.dto.*;
import com.project.familierapi.auth.exception.EmailAlreadyExistsException;
import com.project.familierapi.auth.repository.UserRepository;
import com.project.familierapi.shared.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Value("${application.security.oauth2.google.client-id}")
    private String googleClientId;

    public AuthResponse register(RegisterRequestDto registerRequestDto) {
        userRepository.findByEmail(registerRequestDto.email()).ifPresent(user -> {
            throw new EmailAlreadyExistsException();
        });

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .fullName(registerRequestDto.fullName())
                .email(registerRequestDto.email())
                .passwordHash(passwordEncoder.encode(registerRequestDto.password()))
                .authProvider(AuthProvider.LOCAL)
                .role(Role.USER)
                .isPremium(false)
                .build();

        var savedUser = userRepository.save(user);

        return createAuthResponse(savedUser);
    }

    public AuthResponse loginWithGoogle(String idToken) throws GeneralSecurityException, IOException {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken googleIdToken = verifier.verify(idToken);
        if (googleIdToken == null) {
            throw new IllegalArgumentException("Invalid ID token");
        }

        GoogleIdToken.Payload payload = googleIdToken.getPayload();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String avatarUrl = (String) payload.get("picture");

        User user = userRepository.findByEmail(email)
                .map(existingUser -> {
                    existingUser.setFullName(name);
                    existingUser.setAvatarUrl(avatarUrl);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .id(UUID.randomUUID().toString())
                            .email(email)
                            .fullName(name)
                            .avatarUrl(avatarUrl)
                            .authProvider(AuthProvider.GOOGLE)
                            .role(Role.USER)
                            .isPremium(false)
                            .build();
                    return userRepository.save(newUser);
                });

        return createAuthResponse(user);
    }

    public AuthResponse loginWithEmailAndPassword(String email, String password) {
        var possibleUser = this.userRepository.findByEmail(email);
        if (!possibleUser.isPresent()) {
            throw new BadCredentialsException("Email or password is incorrect");
        }
        
        User user = possibleUser.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Email or password is incorrect");
        }
        return createAuthResponse(user);
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

    private AuthResponse createAuthResponse(User user) {
        var jwtToken = jwtService.generateAccessToken(user);
        var refreshToken = jwtService.generateRefreshToken(user);
        return new AuthResponse(jwtToken, refreshToken, toUserDto(user));
    }
}
