package com.project.familierapi.user.controller;

import com.project.familierapi.auth.dto.UserDto;
import com.project.familierapi.shared.dto.SuccessResponse;
import com.project.familierapi.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<UserDto>> getCurrentUser() {
        UserDto userDto = userService.getCurrentUser();
        SuccessResponse<UserDto> response = new SuccessResponse<>("User details fetched successfully", userDto);
        return ResponseEntity.ok(response);
    }
}
