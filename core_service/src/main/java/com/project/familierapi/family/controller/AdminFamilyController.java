package com.project.familierapi.family.controller;

import com.project.familierapi.family.dto.AdminCreateFamilyRequest;
import com.project.familierapi.family.dto.AdminFamilyResponse;
import com.project.familierapi.family.service.AdminFamilyService;
import com.project.familierapi.shared.dto.SuccessResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/families")
@RequiredArgsConstructor
public class AdminFamilyController {

    private final AdminFamilyService adminFamilyService;

    @GetMapping
    public ResponseEntity<SuccessResponse<Page<AdminFamilyResponse>>> getFamilies(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<AdminFamilyResponse> result = adminFamilyService.getFamilies(keyword, page, size);
        String message = result.isEmpty() ? "No results found" : "Families retrieved successfully";
        return ResponseEntity.ok(new SuccessResponse<>(message, result));
    }

    @PostMapping
    public ResponseEntity<SuccessResponse<AdminFamilyResponse>> createFamily(
            @Valid @RequestBody AdminCreateFamilyRequest request) {
        AdminFamilyResponse response = adminFamilyService.createFamily(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SuccessResponse<>("Family created successfully", response));
    }
}
