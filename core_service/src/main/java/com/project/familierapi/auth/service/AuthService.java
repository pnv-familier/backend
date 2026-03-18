package com.project.familierapi.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.project.familierapi.auth.domain.AuthProvider;
import com.project.familierapi.auth.domain.Token;
import com.project.familierapi.auth.domain.TokenType;
import com.project.familierapi.auth.repository.TokenRepository;
import com.project.familierapi.user.domain.Role;
import com.project.familierapi.user.domain.User;
import com.project.familierapi.auth.dto.*;
import com.project.familierapi.auth.exception.EmailAlreadyExistsException;
import com.project.familierapi.auth.repository.UserRepository;
import com.project.familierapi.shared.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
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
    private final TokenRepository tokenRepository;
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
        if (possibleUser.isEmpty()) {
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

    private void saveUserToken(User user, String jwtToken) {
        var token = Token.builder()
                .user(user)
                .token(jwtToken)
                .tokenType(TokenType.BEARER)
                .expired(false)
                .revoked(false)
                .build();
        tokenRepository.save(token);
    }

    private void revokeAllUserTokens(User user) {
        var validUserTokens = tokenRepository.findAllValidTokenByUser(user.getId());
        if (validUserTokens.isEmpty())
            return;
        validUserTokens.forEach(token -> {
            token.setExpired(true);
            token.setRevoked(true);
        });
        tokenRepository.saveAll(validUserTokens);
    }

    public void refreshToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        final String refreshToken;
        final String userEmail;
        if (authHeader == null ||!authHeader.startsWith("Bearer ")) {
            return;
        }
        refreshToken = authHeader.substring(7);
        userEmail = jwtService.extractUsername(refreshToken);
        if (userEmail != null) {
            var user = this.userRepository.findByEmail(userEmail)
                    .orElseThrow();
            if (jwtService.isTokenValid(refreshToken, user)) {
                var accessToken = jwtService.generateAccessToken(user);
                revokeAllUserTokens(user);
                saveUserToken(user, accessToken);
                var authResponse = new AuthResponse(accessToken, refreshToken, toUserDto(user));
                new ObjectMapper().writeValue(response.getOutputStream(), authResponse);
            }
        }
    }

    private AuthResponse createAuthResponse(User user) {
        var jwtToken = jwtService.generateAccessToken(user);
        var refreshToken = jwtService.generateRefreshToken(user);
        revokeAllUserTokens(user);
        saveUserToken(user, jwtToken);
        return new AuthResponse(jwtToken, refreshToken, toUserDto(user));
    }
}
