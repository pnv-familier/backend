package com.project.familierapi.family.controller;

import com.project.familierapi.family.domain.Family;
import com.project.familierapi.family.dto.CreateFamilyRequestDto;
import com.project.familierapi.family.dto.FamilyResponseDto;
import com.project.familierapi.family.service.FamilyService;
import com.project.familierapi.shared.dto.SuccessResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        Family family = familyService.createFamily(request.name(), userId);
        FamilyResponseDto responseDto = new FamilyResponseDto(family.getId(), family.getName(), family.getInviteCode());
        SuccessResponse<FamilyResponseDto> successResponse = new SuccessResponse<>("Family created successfully", responseDto);
        return ResponseEntity.ok(successResponse);
    }
}