package com.project.familierapi.family.controller;

import com.project.familierapi.family.domain.Family;
import com.project.familierapi.family.dto.CreateFamilyRequestDto;
import com.project.familierapi.family.dto.FamilyResponseDto;
import com.project.familierapi.family.dto.JoinFamilyRequestDto;
import com.project.familierapi.family.dto.MyFamilyResponseDto;
import com.project.familierapi.family.dto.FamilyMemberListResponseDto;
import com.project.familierapi.family.service.FamilyService;
import com.project.familierapi.shared.dto.SuccessResponse;
import com.project.familierapi.user.domain.User;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/api/v1/families")
public class FamilyController {
    private final FamilyService familyService;

    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    @PostMapping("")
    public ResponseEntity<SuccessResponse<FamilyResponseDto>> createFamily(@RequestBody CreateFamilyRequestDto request) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Family family = familyService.createFamily(request.name(), user);
        FamilyResponseDto responseDto = new FamilyResponseDto(family.getId(), family.getName(), family.getInviteCode(), family.getCreatedAt());
        SuccessResponse<FamilyResponseDto> successResponse = new SuccessResponse<>("Family created successfully", responseDto);
        return ResponseEntity.ok(successResponse);
    }

    @GetMapping("/me")
    public ResponseEntity<SuccessResponse<MyFamilyResponseDto>> getMyFamily() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        MyFamilyResponseDto responseDto = familyService.getMyFamily(user);
        SuccessResponse<MyFamilyResponseDto> successResponse = new SuccessResponse<>("Family details retrieved successfully", responseDto);
        return ResponseEntity.ok(successResponse);
    }

    @PostMapping("/join")
    public ResponseEntity<SuccessResponse<MyFamilyResponseDto>> joinFamily(@RequestBody JoinFamilyRequestDto request) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        MyFamilyResponseDto responseDto = familyService.joinFamily(request.inviteCode(), user);
        SuccessResponse<MyFamilyResponseDto> successResponse = new SuccessResponse<>("Successfully joined family", responseDto);
        return ResponseEntity.ok(successResponse);
    }

    @GetMapping("/members")
    public ResponseEntity<SuccessResponse<FamilyMemberListResponseDto>> getFamilyMembers() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        FamilyMemberListResponseDto responseDto = familyService.getFamilyMembers(user);
        SuccessResponse<FamilyMemberListResponseDto> successResponse = new SuccessResponse<>("Family members retrieved successfully", responseDto);
        return ResponseEntity.ok(successResponse);
    }
}