package com.project.familierapi.user.controller;

import com.project.familierapi.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AiUserProfileController {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/user-profile")
    public ResponseEntity<Map<String, Object>> getUserProfile(@RequestParam String email) {
        log.info("REST request for user profile: {}", email);
        return userRepository.findByEmail(email)
                .map(user -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("email", user.getEmail());
                    response.put("fullName", user.getFullName() != null ? user.getFullName() : "");
                    try {
                        String profileJson = user.getProfile() != null ? objectMapper.writeValueAsString(user.getProfile()) : "{}";
                        response.put("profileJson", profileJson);
                    } catch (Exception e) {
                        response.put("profileJson", "{}");
                    }
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
