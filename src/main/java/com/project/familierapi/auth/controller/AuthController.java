package com.project.familierapi.auth.controller;

import com.project.familierapi.auth.dto.*;
import com.project.familierapi.auth.service.AuthService;
import com.project.familierapi.shared.dto.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.GeneralSecurityException;

@RestController
@RequestMapping("api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<SuccessResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequestDto registerRequestDto) {
        AuthResponse authResponse = authService.register(registerRequestDto);
        SuccessResponse<AuthResponse> response = new SuccessResponse<>("User registered successfully", authResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/google")
    public ResponseEntity<SuccessResponse<AuthResponse>> googleLogin(@RequestBody GoogleLoginRequest googleLoginRequest) throws GeneralSecurityException, IOException {
        AuthResponse authResponse = authService.loginWithGoogle(googleLoginRequest.getIdToken());
        SuccessResponse<AuthResponse> response = new SuccessResponse<>("User logged in successfully", authResponse);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<SuccessResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse authResponse = authService.loginWithEmailAndPassword(loginRequest.getEmail(), loginRequest.getPassword());
        SuccessResponse<AuthResponse> response = new SuccessResponse<>("User logged in successfully", authResponse);
        return ResponseEntity.ok(response);
    }
}
