package com.project.familierapi.user.controller;

import com.project.familierapi.auth.dto.UserDto;
import com.project.familierapi.shared.dto.SuccessResponse;
import com.project.familierapi.user.domain.User;
import com.project.familierapi.user.dto.UpdateProfileRequest;
import com.project.familierapi.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<UserDto>> getCurrentUser() {
        UserDto userDto = userService.getCurrentUser();
        SuccessResponse<UserDto> response = new SuccessResponse<>("User details fetched successfully", userDto);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/profile")
    public ResponseEntity<SuccessResponse<UserDto>> updateProfile(
            @AuthenticationPrincipal User currentUser,
            @RequestBody UpdateProfileRequest request) {
        UserDto updatedUserDto = userService.updateProfile(currentUser, request);
        SuccessResponse<UserDto> response = new SuccessResponse<>("User profile updated successfully", updatedUserDto);
        return ResponseEntity.ok(response);
    }
}