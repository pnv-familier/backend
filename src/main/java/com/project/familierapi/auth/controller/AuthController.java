package com.project.familierapi.auth.controller;

import com.project.familierapi.auth.dto.RegisterRequestDto;
import com.project.familierapi.auth.dto.UserDto;
import com.project.familierapi.auth.service.AuthService;
import com.project.familierapi.shared.dto.SuccessResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<SuccessResponse<UserDto>> register(@Valid @RequestBody RegisterRequestDto registerRequestDto) {
        UserDto userDto = authService.register(registerRequestDto);
        SuccessResponse<UserDto> response = new SuccessResponse<>("User registered successfully", userDto);
        return ResponseEntity.ok(response);
    }
}
