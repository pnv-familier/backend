package com.familier.ai.service;

import com.familier.ai.dto.ConfirmSuggestionRequest;
import com.familier.ai.dto.SuggestionDetailResponse;
import com.familier.ai.dto.SuggestionResponse;
import com.familier.ai.entity.*;
import com.familier.ai.repository.ChatSessionRepository;
import com.familier.ai.repository.SuggestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@Slf4j
public class SuggestionService {
    private final SuggestionRepository suggestionRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ObjectMapper objectMapper;

    public SuggestionService(SuggestionRepository suggestionRepository,
            ChatSessionRepository chatSessionRepository,
            ObjectMapper objectMapper) {
        this.suggestionRepository = suggestionRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.objectMapper = objectMapper;
    }

    public Flux<SuggestionResponse> getSuggestions(String email, SuggestionStatus status) {
        Flux<Suggestion> suggestions = status == null
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

    private SuggestionDetailResponse toDetail(Suggestion s) {
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

    public Mono<String> confirmSuggestion(String userEmail, ConfirmSuggestionRequest request) {
        return chatSessionRepository.findById(request.getSessionId())
                .filter(session -> session.getUserEmail().equals(userEmail))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Session not found or unauthorized")))
                .flatMap(session -> {
                    try {
                        SuggestionType type = SuggestionType.valueOf(request.getType());
                        BasePayload payload = parsePayload(request.getType(), request.getPayload());
                        
                        String payloadJson = objectMapper.writeValueAsString(payload);
                        org.bson.Document payloadDoc = org.bson.Document.parse(payloadJson);

                        Suggestion suggestion = Suggestion.builder()
                                .type(type)
                                .receiverEmail(userEmail)
                                .title(extractTitle(type, payload))
                                .description(extractDescription(type, payload))
                                .payload(payloadDoc)
                                .status(SuggestionStatus.PENDING)
                                .createdAt(Instant.now())
                                .triggerContext(request.getTriggerContext())
                                .build();

                        return suggestionRepository.save(suggestion)
                                .map(Suggestion::getId)
                                .doOnSuccess(id -> log.info("Suggestion created: id={}, type={}, receiver={}",
                                        id, type, userEmail));
                    } catch (Exception e) {
                        log.error("Failed to create suggestion", e);
                        return Mono.error(new IllegalArgumentException("Invalid suggestion data: " + e.getMessage()));
                    }
                });
    }

    private BasePayload parsePayload(String type, Object payloadObj) throws Exception {
        String json = objectMapper.writeValueAsString(payloadObj);

        return switch (type) {
            case "EVENT" -> objectMapper.readValue(json, EventPayload.class);
            case "TASK" -> objectMapper.readValue(json, TaskPayload.class);
            case "OFFLINE" -> objectMapper.readValue(json, OfflineSuggestionPayload.class);
            default -> throw new IllegalArgumentException("Unknown suggestion type: " + type);
        };
    }

    private String extractTitle(SuggestionType type, BasePayload payload) {
        return switch (type) {
            case EVENT -> ((EventPayload) payload).getTitle();
            case TASK -> ((TaskPayload) payload).getTitle();
            case OFFLINE -> "Gợi ý kết nối";
        };
    }

    private String extractDescription(SuggestionType type, BasePayload payload) {
        return switch (type) {
            case EVENT -> {
                EventPayload ep = (EventPayload) payload;
                yield String.format("%s - %s, %d/%d/%d tại %s",
                        ep.getStartTime(), ep.getEndTime(), ep.getDate(), ep.getMonth(), ep.getYear(),
                        ep.getLocation() != null ? ep.getLocation() : "Chưa xác định");
            }
            case TASK -> ((TaskPayload) payload).getDescription();
            case OFFLINE -> ((OfflineSuggestionPayload) payload).getAction();
        };
    }
}
