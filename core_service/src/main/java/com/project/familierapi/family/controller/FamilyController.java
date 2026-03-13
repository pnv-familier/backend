package com.project.familierapi.family.controller;

import com.project.familierapi.family.domain.Family;
import com.project.familierapi.family.dto.CreateFamilyRequestDto;
import com.project.familierapi.family.dto.FamilyResponseDto;
import com.project.familierapi.family.dto.JoinFamilyRequestDto;
import com.project.familierapi.family.dto.MyFamilyResponseDto;
import com.project.familierapi.family.dto.FamilyMemberListResponseDto;
import com.project.familierapi.family.dto.FamilyPreviewDto;
import com.project.familierapi.family.service.FamilyService;
import com.project.familierapi.shared.dto.SuccessResponse;
import com.project.familierapi.user.domain.User;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/api/v1/families")
public class FamilyController {
    private final FamilyService familyService;

    private final com.project.familierapi.user.service.UserService userService;

    public FamilyController(FamilyService familyService, com.project.familierapi.user.service.UserService userService) {
        this.familyService = familyService;
        this.userService = userService;
    }

    @PostMapping("")
    public ResponseEntity<SuccessResponse<FamilyResponseDto>> createFamily(
            @RequestHeader("X-User-Email") String userEmail,
            @RequestBody CreateFamilyRequestDto request) {
        User user = userService.getUserByEmail(userEmail);
        Family family = familyService.createFamily(request.name(), user);
        FamilyResponseDto responseDto = new FamilyResponseDto(family.getId(), family.getName(), family.getInviteCode(), family.getCreatedAt());
        SuccessResponse<FamilyResponseDto> successResponse = new SuccessResponse<>("Family created successfully", responseDto);
        return ResponseEntity.ok(successResponse);
    }

    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<MyFamilyResponseDto>> getMyFamily(
            @RequestHeader("X-User-Email") String userEmail) {
        User user = userService.getUserByEmail(userEmail);
        MyFamilyResponseDto responseDto = familyService.getMyFamily(user);
        SuccessResponse<MyFamilyResponseDto> successResponse = new SuccessResponse<>("Family details retrieved successfully", responseDto);
        return ResponseEntity.ok(successResponse);
    }

    @PostMapping("/join")
    public ResponseEntity<SuccessResponse<MyFamilyResponseDto>> joinFamily(
            @RequestHeader("X-User-Email") String userEmail,
            @RequestBody JoinFamilyRequestDto request) {
        User user = userService.getUserByEmail(userEmail);
        MyFamilyResponseDto responseDto = familyService.joinFamily(request.joinCode(), request.relationship(), user);
        SuccessResponse<MyFamilyResponseDto> successResponse = new SuccessResponse<>("Successfully joined family", responseDto);
        return ResponseEntity.ok(successResponse);
    }

    @GetMapping("/members")
    public ResponseEntity<SuccessResponse<FamilyMemberListResponseDto>> getFamilyMembers(
            @RequestHeader("X-User-Email") String userEmail) {
        User user = userService.getUserByEmail(userEmail);
        FamilyMemberListResponseDto responseDto = familyService.getFamilyMembers(user);
        SuccessResponse<FamilyMemberListResponseDto> successResponse = new SuccessResponse<>("Family members retrieved successfully", responseDto);
        return ResponseEntity.ok(successResponse);
    }

    @GetMapping("/preview/{joinCode}")
    public ResponseEntity<SuccessResponse<FamilyPreviewDto>> getFamilyPreview(
            @PathVariable String joinCode) {
        FamilyPreviewDto responseDto = familyService.getFamilyPreview(joinCode);
        SuccessResponse<FamilyPreviewDto> successResponse = new SuccessResponse<>("Family preview retrieved successfully", responseDto);
        return ResponseEntity.ok(successResponse);
    }

    @GetMapping("/members-for-mention")
    public ResponseEntity<SuccessResponse<com.project.familierapi.family.dto.FamilyMembersForMentionDto>> getMembersForMention(
            @RequestHeader("X-User-Email") String userEmail) {
        User user = userService.getUserByEmail(userEmail);
        com.project.familierapi.family.dto.FamilyMembersForMentionDto responseDto = familyService.getMembersForMention(user);
        SuccessResponse<com.project.familierapi.family.dto.FamilyMembersForMentionDto> successResponse = new SuccessResponse<>("Family members retrieved successfully", responseDto);
        return ResponseEntity.ok(successResponse);
    }
}