package com.project.familierapi.admin.controller;

import com.project.familierapi.admin.dto.CoreOverviewResponse;
import com.project.familierapi.admin.service.AdminOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminOverviewService adminOverviewService;

    @GetMapping("/core-overview")
    public ResponseEntity<CoreOverviewResponse> getCoreOverview() {
        return ResponseEntity.ok(adminOverviewService.getCoreOverview());
    }
}
