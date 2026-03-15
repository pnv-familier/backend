package com.familier.ai.controller;

import com.familier.ai.dto.SuggestionDetailResponse;
import com.familier.ai.dto.SuggestionResponse;
import com.familier.ai.entity.SuggestionStatus;
import com.familier.ai.service.SuggestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/ai/suggestions")
public class SuggestionController {

    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    // AC016.1, AC016.2, AC016.3, AC016.4, AC016.5, AC016.6, AC016.7, AC016.9
    @GetMapping
    public Flux<SuggestionResponse> getSuggestions(
            @RequestHeader("X-User-Email") String email,
            @RequestParam(required = false) SuggestionStatus status) {
        return suggestionService.getSuggestions(email, status);
    }

    // AC016.8, AC016.10, AC016.11, AC016.12, AC016.13, AC016.14, AC016.15, AC016.16
    @GetMapping("/{id}")
    public Mono<ResponseEntity<SuggestionDetailResponse>> getSuggestionDetail(
            @PathVariable String id,
            @RequestHeader("X-User-Email") String email) {
        return suggestionService.getSuggestionDetail(id, email)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build());
    }

    // AC016.17 - accept suggestion (Create Love Task / Add to Schedule)
    @PostMapping("/{id}/accept")
    public Mono<ResponseEntity<SuggestionDetailResponse>> acceptSuggestion(
            @PathVariable String id,
            @RequestHeader("X-User-Email") String email) {
        return suggestionService.acceptSuggestion(id, email)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build());
    }
}
