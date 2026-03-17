package com.familier.ai.controller;

import com.familier.ai.dto.ChatMessageDto;
import com.familier.ai.dto.FamilyMembersDto;
import com.familier.ai.entity.ChatMessage;
import com.familier.ai.entity.ChatSession;
import com.familier.ai.entity.Sender;
import com.familier.ai.repository.ChatMessageRepository;
import com.familier.ai.repository.ChatSessionRepository;
import com.familier.ai.service.ContextManagerService;
import com.familier.ai.service.GeminiService;
import com.familier.ai.service.PromptService;
import com.familier.ai.service.SuggestionService;
import com.familier.ai.service.SummarizationScheduler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AiController {

    private static final Pattern METADATA_PATTERN = Pattern.compile("<suggestion_metadata>(.*?)</suggestion_metadata>",
            Pattern.DOTALL);
    private static final Pattern SUGGESTIONS_PATTERN = Pattern.compile("<suggestions>(.*?)</suggestions>",
            Pattern.DOTALL);
    
    private final GeminiService geminiService;
    private final PromptService promptService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ContextManagerService contextManagerService;
    private final SummarizationScheduler summarizationScheduler;
    private final SuggestionService suggestionService;
    private final WebClient.Builder webClientBuilder;
    private final String coreServiceUrl;
    private final String internalSecret;

    public AiController(GeminiService geminiService,
            PromptService promptService,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            SummarizationScheduler summarizationScheduler,
            ContextManagerService contextManagerService,
            SuggestionService suggestionService,
            WebClient.Builder webClientBuilder,
            @Value("${CORE_SERVICE_URL}") String coreServiceUrl,
            @Value("${application.security.internal.secret}") String internalSecret) {
        this.geminiService = geminiService;
        this.promptService = promptService;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.summarizationScheduler = summarizationScheduler;
        this.contextManagerService = contextManagerService;
        this.suggestionService = suggestionService;
        this.webClientBuilder = webClientBuilder;
        this.coreServiceUrl = coreServiceUrl;
        this.internalSecret = internalSecret;
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<ServerSentEvent<String>>>> streamAiResponse(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String taggedUserEmail,
            @RequestHeader(name = "X-User-Email") String email) throws Exception {

        return getOrCreateSession(sessionId, message, email)
                .flatMap(session -> contextManagerService
                        .buildVariables(email, session.getId(), message, taggedUserEmail)
                        .flatMap(result -> {
                            try {
                                boolean includeSuggestion = result.getDetection().getSuggestion().isHasSuggestion()
                                        && result.getDetection().getSuggestion().getConfidence() > 0.6;
                                String suggestionType = includeSuggestion
                                        ? result.getDetection().getSuggestion().getType()
                                        : null;

                                String enrichedPrompt = promptService.loadSystemPrompt("virtual_member_v3",
                                        result.getVariables(), includeSuggestion, suggestionType);

                                return Mono.just(ResponseEntity.ok()
                                        .header("X-Session-Id", session.getId())
                                        .body(saveUserMessage(session.getId(), message)
                                                .flatMapMany(savedMsg -> executeAiStream(session.getId(), message,
                                                        enrichedPrompt,
                                                        result.getTargetUserEmail(), suggestionType, email))));
                            } catch (Exception e) {
                                return Mono.error(e);
                            }
                        }));
    }

    private Mono<ChatMessage> saveUserMessage(String sessionId, String content) {
        ChatMessage userMessage = ChatMessage.builder()
                .sessionId(sessionId)
                .sender(Sender.USER)
                .content(content)
                .suggestions(null)
                .timestamp(Instant.now())
                .build();
        return chatMessageRepository.save(userMessage)
                .flatMap(msg -> updateSessionLastUpdate(sessionId).thenReturn(msg));
    }

    private Mono<Void> updateSessionLastUpdate(String sessionId) {
        return chatSessionRepository.findById(sessionId)
                .flatMap(session -> {
                    session.setLastUpdate(Instant.now());
                    if ("COMPLETED".equals(session.getStatus())) {
                        session.setStatus("ACTIVE");
                    }
                    return chatSessionRepository.save(session);
                })
                .then();
    }

    private Flux<ServerSentEvent<String>> executeAiStream(String sessionId, String message, String systemPrompt,
            String targetUserEmail, String suggestionType, String userEmail) {
        StringBuilder contentAccumulator = new StringBuilder();
        StringBuilder tagBuffer = new StringBuilder();
        String[] currentTag = {null};
        
        return geminiService.streamGenerateContent(systemPrompt, message)
                .concatMap(event -> {
                    String data = event.data();
                    
                    if (data == null || data.equals("[DONE.]")) {
                        return Flux.empty();
                    }

                    List<ServerSentEvent<String>> events = new ArrayList<>();
                    
                    if (currentTag[0] != null) {
                        tagBuffer.append(data);
                        
                        String closingTag = currentTag[0].equals("suggestions") 
                                ? "</suggestions>" 
                                : "</suggestion_metadata>";
                        
                        if (tagBuffer.toString().contains(closingTag)) {
                            String buffered = tagBuffer.toString();
                            Pattern pattern = currentTag[0].equals("suggestions")
                                    ? SUGGESTIONS_PATTERN
                                    : METADATA_PATTERN;
                            
                            Matcher matcher = pattern.matcher(buffered);
                            if (matcher.find()) {
                                String extracted = matcher.group(1).trim();
                                extracted = decodeHtmlEntities(extracted);
                                
                                if (currentTag[0].equals("metadata")) {
                                    if (suggestionType != null) {
                                        extracted = injectTypeField(extracted, suggestionType);
                                    }
                                    
                                    if (suggestionType != null && suggestionType.equals("TASK") 
                                            && targetUserEmail != null && !targetUserEmail.isEmpty()) {
                                        extracted = injectAssigneeEmail(extracted, targetUserEmail);
                                    }
                                    
                                    contentAccumulator.append("<suggestion_metadata>").append(extracted).append("</suggestion_metadata>");
                                    
                                    if (isValidJson(extracted) && !"TASK".equals(suggestionType)) {
                                        events.add(ServerSentEvent.<String>builder()
                                                .event("metadata")
                                                .data(extracted)
                                                .build());
                                    }
                                } else {
                                    contentAccumulator.append("<suggestions>").append(extracted).append("</suggestions>");
                                }
                                
                                String afterTag = buffered.substring(buffered.indexOf(closingTag) + closingTag.length());
                                if (!afterTag.isEmpty()) {
                                    contentAccumulator.append(afterTag);
                                    events.add(ServerSentEvent.<String>builder()
                                            .event("message")
                                            .data(afterTag)
                                            .build());
                                }
                            }
                            
                            currentTag[0] = null;
                            tagBuffer.setLength(0);
                        }
                        return Flux.fromIterable(events);
                    }
                    
                    int suggestionsStart = data.indexOf("<suggestions>");
                    int metadataStart = data.indexOf("<suggestion_metadata>");
                    
                    int tagStart = -1;
                    String tagType = null;
                    
                    if (suggestionsStart >= 0 && metadataStart >= 0) {
                        if (suggestionsStart < metadataStart) {
                            tagStart = suggestionsStart;
                            tagType = "suggestions";
                        } else {
                            tagStart = metadataStart;
                            tagType = "metadata";
                        }
                    } else if (suggestionsStart >= 0) {
                        tagStart = suggestionsStart;
                        tagType = "suggestions";
                    } else if (metadataStart >= 0) {
                        tagStart = metadataStart;
                        tagType = "metadata";
                    }
                    
                    if (tagStart >= 0) {
                        String beforeTag = data.substring(0, tagStart);
                        if (!beforeTag.isEmpty()) {
                            contentAccumulator.append(beforeTag);
                            events.add(ServerSentEvent.<String>builder()
                                    .event("message")
                                    .data(beforeTag)
                                    .build());
                        }
                        
                        String fromTag = data.substring(tagStart);
                        currentTag[0] = tagType;
                        tagBuffer.append(fromTag);
                        
                        String closingTag = tagType.equals("suggestions") 
                                ? "</suggestions>" 
                                : "</suggestion_metadata>";
                        
                        if (fromTag.contains(closingTag)) {
                            Pattern pattern = tagType.equals("suggestions")
                                    ? SUGGESTIONS_PATTERN
                                    : METADATA_PATTERN;
                            
                            Matcher matcher = pattern.matcher(tagBuffer.toString());
                            if (matcher.find()) {
                                String extracted = matcher.group(1).trim();
                                extracted = decodeHtmlEntities(extracted);
                                
                                if (tagType.equals("metadata")) {
                                    if (suggestionType != null) {
                                        extracted = injectTypeField(extracted, suggestionType);
                                    }
                                    
                                    if (suggestionType != null && suggestionType.equals("TASK") 
                                            && targetUserEmail != null && !targetUserEmail.isEmpty()) {
                                        extracted = injectAssigneeEmail(extracted, targetUserEmail);
                                    }
                                    
                                    contentAccumulator.append("<suggestion_metadata>").append(extracted).append("</suggestion_metadata>");
                                    
                                    if (isValidJson(extracted) && !"TASK".equals(suggestionType)) {
                                        events.add(ServerSentEvent.<String>builder()
                                                .event("metadata")
                                                .data(extracted)
                                                .build());
                                    }
                                } else {
                                    contentAccumulator.append("<suggestions>").append(extracted).append("</suggestions>");
                                }
                                
                                String afterTag = tagBuffer.toString().substring(
                                        tagBuffer.toString().indexOf(closingTag) + closingTag.length());
                                if (!afterTag.isEmpty()) {
                                    contentAccumulator.append(afterTag);
                                    events.add(ServerSentEvent.<String>builder()
                                            .event("message")
                                            .data(afterTag)
                                            .build());
                                }
                            }
                            
                            currentTag[0] = null;
                            tagBuffer.setLength(0);
                        }
                        
                        return Flux.fromIterable(events);
                    }
                    
                    contentAccumulator.append(data);
                    events.add(ServerSentEvent.<String>builder()
                            .event("message")
                            .data(data)
                            .build());
                    
                    return Flux.fromIterable(events);
                })
                .concatWith(Flux.defer(() -> {
                    String fullContent = contentAccumulator.toString();
                    
                    List<ServerSentEvent<String>> finalEvents = new ArrayList<>();
                    
                    Object metadata = null;
                    if ("TASK".equals(suggestionType)) {
                        Matcher metadataMatcher = METADATA_PATTERN.matcher(fullContent);
                        if (metadataMatcher.find()) {
                            String metadataJson = metadataMatcher.group(1).trim();
                            metadataJson = decodeHtmlEntities(metadataJson);
                            try {
                                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                metadata = mapper.readValue(metadataJson, Object.class);
                            } catch (Exception e) {
                                log.error("Failed to parse metadata", e);
                            }
                        }
                    }
                    
                    String suggestionsJson = null;
                    Matcher suggestionsMatcher = SUGGESTIONS_PATTERN.matcher(fullContent);
                    if (suggestionsMatcher.find()) {
                        suggestionsJson = suggestionsMatcher.group(1).trim();
                        fullContent = suggestionsMatcher.replaceAll("");
                        
                        finalEvents.add(ServerSentEvent.<String>builder()
                                .event("suggestions")
                                .data(suggestionsJson)
                                .build());
                    }
                    
                    String cleanContent = fullContent.trim();
                    List<String> suggestionsList = parseSuggestionsArray(suggestionsJson);
                    
                    persistAiResponse(sessionId, cleanContent, suggestionsList);
                    
                    if ("TASK".equals(suggestionType) && metadata != null) {
                        createSuggestionsForFamilyMembers(userEmail, suggestionType, metadata);
                    }
                    
                    finalEvents.add(ServerSentEvent.<String>builder()
                            .event("done")
                            .data("[DONE]")
                            .build());
                    
                    return Flux.fromIterable(finalEvents);
                }))
                .doOnError(e -> {
                    log.error("Error in AI stream: {}", e.getMessage(), e);
                    String content = contentAccumulator.toString();
                    content = content.replaceAll("<suggestions>.*?</suggestions>", "");
                    content = content.replaceAll("<suggestion_metadata>.*?</suggestion_metadata>", "");
                    persistAiResponse(sessionId, content.trim(), null);
                })
                .onErrorResume(e -> {
                    log.error("Gemini API error: {}", e.getMessage(), e);
                    
                    String errorMessage;
                    String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    
                    if (errorMsg.contains("model") || errorMsg.contains("gemini") || 
                        errorMsg.contains("ai") || errorMsg.contains("generation")) {
                        errorMessage = "Hiện tại Familier đang gặp lỗi với model AI, vui lòng thử lại sau.";
                    } else {
                        errorMessage = "Hiện tại Familier đang gặp vấn đề, vui lòng thử lại sau.";
                    }
                    
                    return Flux.just(
                        ServerSentEvent.<String>builder()
                            .event("message")
                            .data(errorMessage)
                            .build(),
                        ServerSentEvent.<String>builder()
                            .event("done")
                            .data("[DONE]")
                            .build()
                    );
                });
    }

    private String decodeHtmlEntities(String text) {
        if (text == null) return null;
        
        String decoded = text;
        for (int i = 0; i < 3; i++) {
            String temp = decoded
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&#39;", "'")
                    .replace("&#x27;", "'")
                    .replace("&#x2F;", "/");
            
            if (temp.equals(decoded)) break;
            decoded = temp;
        }
        return decoded;
    }

    private String injectTypeField(String metadata, String type) {
        try {
            if (!metadata.contains("\"type\"")) {
                int insertPos = metadata.indexOf("{") + 1;
                return metadata.substring(0, insertPos) + " \"type\": \"" + type + "\"," + metadata.substring(insertPos);
            }
        } catch (Exception e) {
            log.error("Failed to inject type field", e);
        }
        return metadata;
    }

    private List<String> parseSuggestionsArray(String suggestionsJson) {
        if (suggestionsJson == null || suggestionsJson.trim().isEmpty()) {
            return null;
        }
        
        try {
            String decoded = decodeHtmlEntities(suggestionsJson);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String[] array = mapper.readValue(decoded, String[].class);
            return Arrays.asList(array);
        } catch (Exception e) {
            log.error("Failed to parse suggestions JSON: {}", suggestionsJson, e);
            return null;
        }
    }

    private boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(json);
            return true;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return false;
        }
    }

    private String injectAssigneeEmail(String metadata, String assigneeEmail) {
        try {
            if (!metadata.contains("assigneeEmail")) {
                int insertPos = metadata.indexOf("{") + 1;
                return metadata.substring(0, insertPos) + " \"assigneeEmail\": \"" + assigneeEmail + "\","
                        + metadata.substring(insertPos);
            }
        } catch (Exception e) {
            log.error("Failed to inject assigneeEmail", e);
        }
        return metadata;
    }

    private void persistAiResponse(String sessionId, String content, List<String> suggestions) {
        if (content == null || content.isEmpty()) {
            return;
        }

        ChatMessage aiMessage = ChatMessage.builder()
                .sessionId(sessionId)
                .sender(Sender.AI)
                .content(content)
                .suggestions(suggestions)
                .timestamp(Instant.now())
                .build();
        
        chatMessageRepository.save(aiMessage)
                .flatMap(msg -> updateSessionLastUpdate(sessionId).thenReturn(msg))
                .subscribe(
                    saved -> {},
                    error -> log.error("Failed to save AI message", error)
                );
    }

    private void createSuggestionsForFamilyMembers(String userEmail, String suggestionType, Object metadata) {
        if (!"TASK".equals(suggestionType)) {
            return;
        }

        WebClient webClient = webClientBuilder.baseUrl(coreServiceUrl).build();
        webClient.get()
                .uri("/api/v1/families/members-for-mention")
                .header("X-Internal-Secret", internalSecret)
                .header("X-User-Email", userEmail)
                .retrieve()
                .bodyToMono(FamilyMembersDto.class)
                .subscribe(
                    response -> {
                        if (response.getMembers() != null && !response.getMembers().isEmpty()) {
                            response.getMembers().stream()
                                    .filter(member -> !member.getEmail().equals(userEmail))
                                    .forEach(member -> {
                                        suggestionService.createSuggestion(
                                                member.getEmail(),
                                                com.familier.ai.entity.SuggestionType.TASK,
                                                metadata,
                                                "auto-created"
                                        ).subscribe(
                                            id -> log.info("Suggestion created for member: {}, id={}", member.getEmail(), id),
                                            error -> log.error("Failed to create suggestion for member: {}", member.getEmail(), error)
                                        );
                                    });
                        }
                    },
                    error -> log.error("Failed to fetch family members: {}", error.getMessage())
                );
    }

    @GetMapping("/sessions")
    public Flux<ChatSession> getSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(name = "X-User-Email") String email) {
        return chatSessionRepository.findAllByUserEmailOrderByCreatedAtDesc(email, PageRequest.of(page, size));
    }

    @GetMapping("/history/{sessionId}")
    public Flux<ChatMessageDto> getHistory(
            @PathVariable String sessionId,
            @RequestHeader(name = "X-User-Email") String email) {
        return chatSessionRepository.findById(sessionId)
                .filter(session -> session.getUserEmail().equals(email))
                .flatMapMany(session -> chatMessageRepository.findAllBySessionIdOrderByTimestampAsc(sessionId))
                .map(ChatMessageDto::fromEntity);
    }

    @PostMapping("/sessions/{sessionId}/summarize")
    public Mono<ResponseEntity<Void>> summarizeSession(
            @PathVariable String sessionId,
            @RequestHeader(name = "X-User-Email") String email) {
        return chatSessionRepository.findById(sessionId)
                .filter(session -> session.getUserEmail().equals(email))
                .flatMap(session -> Mono.fromRunnable(() -> summarizationScheduler.summarizeOldActiveSessions()))
                .then(Mono.just(ResponseEntity.ok().<Void>build()))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private Mono<ChatSession> getOrCreateSession(String sessionId, String firstMessage, String userEmail) {
        if (sessionId != null && !sessionId.isEmpty()) {
            return chatSessionRepository.findById(sessionId);
        }

        String targetContext = firstMessage.length() > 30 ? firstMessage.substring(0, 30) : firstMessage;
        ChatSession newSession = ChatSession.builder()
                .userEmail(userEmail)
                .targetContext(targetContext)
                .createdAt(Instant.now())
                .status("ACTIVE")
                .lastUpdate(Instant.now())
                .lastSummarizedAt(Instant.now())
                .build();
        return chatSessionRepository.save(newSession);
    }
}
