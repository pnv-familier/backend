package com.familier.ai.controller;

import com.familier.ai.dto.ReportRequest;
import com.familier.ai.entity.Report;
import com.familier.ai.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/ai/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<Report>> createReport(
            @Valid @RequestBody ReportRequest request,
            @RequestHeader("X-User-Email") String email) {
        return reportService.createReport(request, email)
                .map(report -> ResponseEntity.status(HttpStatus.CREATED).body(report));
    }
}
