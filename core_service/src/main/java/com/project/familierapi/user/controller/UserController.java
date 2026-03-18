package com.project.familierapi.user.controller;

import com.project.familierapi.auth.dto.UserDto;
import com.project.familierapi.shared.dto.SuccessResponse;
import com.project.familierapi.user.domain.User;
import com.project.familierapi.user.dto.UpdateProfileRequest;
import com.project.familierapi.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<UserDto>> getCurrentUser(
            @RequestHeader("X-User-Email") String userEmail) {
        User user = userService.getUserByEmail(userEmail);
        UserDto userDto = userService.getCurrentUser(user);
        SuccessResponse<UserDto> response = new SuccessResponse<>("User details fetched successfully", userDto);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    public ResponseEntity<SuccessResponse<UserDto>> updateProfile(
            @RequestHeader("X-User-Email") String userEmail,
            @Valid @RequestBody UpdateProfileRequest request) {
        User user = userService.getUserByEmail(userEmail);
        UserDto updatedUserDto = userService.updateProfile(user, request);
        SuccessResponse<UserDto> response = new SuccessResponse<>("User profile updated successfully", updatedUserDto);
        return ResponseEntity.ok(response);
    }
}