package com.familier.ai.service;

import com.familier.ai.dto.SuggestionDetailResponse;
import com.familier.ai.dto.SuggestionResponse;
import com.familier.ai.entity.SuggestionStatus;
import com.familier.ai.repository.SuggestionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class SuggestionService {
    private final SuggestionRepository suggestionRepository;

    public SuggestionService(SuggestionRepository suggestionRepository) {
        this.suggestionRepository = suggestionRepository;
    }

    public Flux<SuggestionResponse> getSuggestions(String email, SuggestionStatus status) {
        Flux<com.familier.ai.entity.Suggestion> suggestions = status == null
                ? suggestionRepository.findByReceiverEmailOrderByCreatedAtDesc(email)
                : suggestionRepository.findByReceiverEmailAndStatusOrderByCreatedAtDesc(email, status);

        return suggestions.map(s -> SuggestionResponse.builder()
                .id(s.getId())
                .title(s.getTitle())
                .description(s.getDescription())
                .type(s.getType())
                .status(s.getStatus())
                .createdAt(s.getCreatedAt())
                .build());
    }

    private SuggestionDetailResponse toDetail(com.familier.ai.entity.Suggestion s) {
        return SuggestionDetailResponse.builder()
                .id(s.getId())
                .title(s.getTitle())
                .description(s.getDescription())
                .triggerContext(s.getTriggerContext())
                .type(s.getType())
                .status(s.getStatus())
                .payload(s.getPayload() != null ? s.getPayload() : null)
                .createdAt(s.getCreatedAt())
                .expiredAt(s.getExpiredAt())
                .build();
    }

    public Mono<SuggestionDetailResponse> getSuggestionDetail(String id, String email) {
        return suggestionRepository.findById(id)
                .filter(s -> s.getReceiverEmail().equals(email))
                .map(this::toDetail)
                .switchIfEmpty(Mono.error(new RuntimeException("Suggestion not found")));
    }

    public Mono<SuggestionDetailResponse> acceptSuggestion(String id, String email) {
        return suggestionRepository.findById(id)
                .filter(s -> s.getReceiverEmail().equals(email))
                .flatMap(s -> {
                    s.setStatus(SuggestionStatus.ACCEPTED);
                    return suggestionRepository.save(s);
                })
                .map(this::toDetail)
                .switchIfEmpty(Mono.error(new RuntimeException("Suggestion not found")));
    }
}
