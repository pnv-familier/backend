package com.project.familierapi.user.controller;

import com.project.familierapi.auth.repository.UserRepository;
import com.project.familierapi.family.repository.RelationshipInferenceRepository;
import com.project.familierapi.family.service.RelationshipMappingService;
import com.project.familierapi.user.dto.UserProfileWithRelationDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AiUserProfileController {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final RelationshipInferenceRepository relationshipInferenceRepository;
    private final RelationshipMappingService relationshipMappingService;

    @GetMapping("/user-profile")
    public ResponseEntity<Map<String, Object>> getUserProfile(@RequestParam String email) {
        log.info("REST request for user profile: {}", email);
        return userRepository.findByEmail(email)
                .map(user -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("email", user.getEmail());
                    response.put("fullName", user.getFullName() != null ? user.getFullName() : "");
                    try {
                        String hobbiesJson = user.getHobbies() != null ? objectMapper.writeValueAsString(user.getHobbies()) : "[]";
                        response.put("hobbies", hobbiesJson);
                    } catch (Exception e) {
                        response.put("hobbies", "[]");
                    }
                    response.put("birthday", user.getDateOfBirth() != null ? user.getDateOfBirth().toLocalDate().toString() : "");
                    response.put("gender", user.getGender() != null ? user.getGender().name() : "");
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user-profile-with-relation")
    public ResponseEntity<UserProfileWithRelationDto> getUserProfileWithRelation(
            @RequestParam String currentUserEmail,
            @RequestParam String targetUserEmail) {
        log.info("REST request for user profile with relation: current={}, target={}", currentUserEmail, targetUserEmail);
        
        return userRepository.findByEmail(targetUserEmail)
                .map(targetUser -> {
                    UserProfileWithRelationDto dto = UserProfileWithRelationDto.builder()
                            .email(targetUser.getEmail())
                            .fullName(targetUser.getFullName() != null ? targetUser.getFullName() : "")
                            .birthday(targetUser.getDateOfBirth() != null 
                                    ? targetUser.getDateOfBirth().format(DateTimeFormatter.ISO_LOCAL_DATE) 
                                    : "")
                            .gender(targetUser.getGender() != null ? targetUser.getGender().name() : "")
                            .build();
                    
                    try {
                        String hobbiesJson = targetUser.getHobbies() != null 
                                ? objectMapper.writeValueAsString(targetUser.getHobbies()) 
                                : "[]";
                        dto.setHobbies(hobbiesJson);
                    } catch (Exception e) {
                        log.error("Error serializing hobbies JSON", e);
                        dto.setHobbies("[]");
                    }
                    
                    // Get relationship from current user perspective
                    if (currentUserEmail.equals(targetUserEmail)) {
                        dto.setRelationType("SELF");
                    } else {
                        relationshipInferenceRepository.findByUser1EmailAndUser2Email(currentUserEmail, targetUserEmail)
                                .ifPresent(relation -> dto.setRelationType(relation.getRelationType().name()));
                    }
                    
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/map-relation-to-email")
    public ResponseEntity<Map<String, String>> mapRelationToEmail(
            @RequestParam String currentUserEmail,
            @RequestParam String relationType) {
        log.info("REST request to map relation to email: user={}, relation={}", currentUserEmail, relationType);
        
        return relationshipMappingService.mapRelationToEmail(currentUserEmail, relationType)
                .map(targetEmail -> {
                    Map<String, String> response = new HashMap<>();
                    response.put("targetEmail", targetEmail);
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
