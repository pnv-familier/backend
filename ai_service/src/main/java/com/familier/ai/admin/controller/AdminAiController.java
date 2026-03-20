package com.familier.ai.admin.controller;

import com.familier.ai.admin.dto.AiOverviewResponse;
import com.familier.ai.admin.service.AdminAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/ai/api/v1/admin")
@RequiredArgsConstructor
public class AdminAiController {

    private final AdminAiService adminAiService;

    @GetMapping("/ai-overview")
    public Mono<ResponseEntity<AiOverviewResponse>> getAiOverview(
            @RequestHeader("X-User-Role") String role) {
        if (!"ROLE_ADMIN".equals(role))
            return Mono.just(ResponseEntity.status(403).<AiOverviewResponse>build());
        return adminAiService.getAiOverview().map(ResponseEntity::ok);
    }
}
