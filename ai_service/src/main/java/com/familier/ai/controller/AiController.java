package com.familier.ai.controller;

import com.familier.ai.dto.ChatMessageDto;
import com.familier.ai.entity.ChatMessage;
import com.familier.ai.entity.ChatSession;
import com.familier.ai.entity.Sender;
import com.familier.ai.repository.ChatMessageRepository;
import com.familier.ai.repository.ChatSessionRepository;
import com.familier.ai.service.ContextManagerService;
import com.familier.ai.service.GeminiService;
import com.familier.ai.service.PromptService;
import com.familier.ai.service.SummarizationScheduler;

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

    public AiController(GeminiService geminiService,
            PromptService promptService,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            SummarizationScheduler summarizationScheduler,
            ContextManagerService contextManagerService) {
        this.geminiService = geminiService;
        this.promptService = promptService;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.summarizationScheduler = summarizationScheduler;
        this.contextManagerService = contextManagerService;
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
                                                        result.getTargetUserEmail(), suggestionType))));
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

    /**
     * Execute AI stream with real-time chunk processing:
     * 1. Stream chunks immediately until detecting tag start
     * 2. Buffer content inside tags (<suggestions> and <suggestion_metadata>)
     * 3. Extract and send as separate events when tags close
     * 4. Continue streaming after tags
     */
    private Flux<ServerSentEvent<String>> executeAiStream(String sessionId, String message, String systemPrompt,
            String targetUserEmail, String suggestionType) {
        
        StringBuilder contentAccumulator = new StringBuilder();
        StringBuilder tagBuffer = new StringBuilder();
        String[] currentTag = {null}; // "suggestions" or "metadata"
        
        return geminiService.streamGenerateContent(systemPrompt, message)
                .concatMap(event -> {
                    String data = event.data();
                    
                    if (data == null || data.equals("[DONE.]")) {
                        return Flux.empty();
                    }

                    List<ServerSentEvent<String>> events = new ArrayList<>();
                    
                    // If currently buffering a tag
                    if (currentTag[0] != null) {
                        tagBuffer.append(data);
                        
                        // Check if tag is closed
                        String closingTag = currentTag[0].equals("suggestions") 
                                ? "</suggestions>" 
                                : "</suggestion_metadata>";
                        
                        if (tagBuffer.toString().contains(closingTag)) {
                            String buffered = tagBuffer.toString();
                            
                            // Extract tag content
                            Pattern pattern = currentTag[0].equals("suggestions")
                                    ? SUGGESTIONS_PATTERN
                                    : METADATA_PATTERN;
                            
                            Matcher matcher = pattern.matcher(buffered);
                            if (matcher.find()) {
                                String extracted = matcher.group(1).trim();
                                extracted = decodeHtmlEntities(extracted);
                                
                                // Handle metadata injection
                                if (currentTag[0].equals("metadata")) {
                                    // Inject type field
                                    if (suggestionType != null) {
                                        extracted = injectTypeField(extracted, suggestionType);
                                    }
                                    
                                    // Inject assigneeEmail for TASK type
                                    if (suggestionType != null && suggestionType.equals("TASK") 
                                            && targetUserEmail != null && !targetUserEmail.isEmpty()) {
                                        extracted = injectAssigneeEmail(extracted, targetUserEmail);
                                    }
                                    
                                    if (isValidJson(extracted)) {
                                        events.add(ServerSentEvent.<String>builder()
                                                .event("metadata")
                                                .data(extracted)
                                                .build());
                                        log.debug("Sent metadata event");
                                    }
                                } else {
                                    // suggestions tag - will be sent at the end
                                    contentAccumulator.append("<suggestions>").append(extracted).append("</suggestions>");
                                }
                                
                                // Get content after closing tag
                                String afterTag = buffered.substring(buffered.indexOf(closingTag) + closingTag.length());
                                if (!afterTag.isEmpty()) {
                                    contentAccumulator.append(afterTag);
                                    events.add(ServerSentEvent.<String>builder()
                                            .event("message")
                                            .data(afterTag)
                                            .build());
                                }
                            }
                            
                            // Reset buffer
                            currentTag[0] = null;
                            tagBuffer.setLength(0);
                        }
                        // Still inside tag, continue buffering
                        return Flux.fromIterable(events);
                    }
                    
                    // Check if chunk contains tag start
                    int suggestionsStart = data.indexOf("<suggestions>");
                    int metadataStart = data.indexOf("<suggestion_metadata>");
                    
                    // Determine which tag appears first (if any)
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
                        // Stream content before tag
                        String beforeTag = data.substring(0, tagStart);
                        if (!beforeTag.isEmpty()) {
                            contentAccumulator.append(beforeTag);
                            events.add(ServerSentEvent.<String>builder()
                                    .event("message")
                                    .data(beforeTag)
                                    .build());
                        }
                        
                        // Start buffering from tag
                        String fromTag = data.substring(tagStart);
                        currentTag[0] = tagType;
                        tagBuffer.append(fromTag);
                        
                        // Check if tag closes in same chunk
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
                                    // Inject type field
                                    if (suggestionType != null) {
                                        extracted = injectTypeField(extracted, suggestionType);
                                    }
                                    
                                    // Inject assigneeEmail for TASK type
                                    if (suggestionType != null && suggestionType.equals("TASK") 
                                            && targetUserEmail != null && !targetUserEmail.isEmpty()) {
                                        extracted = injectAssigneeEmail(extracted, targetUserEmail);
                                    }
                                    
                                    if (isValidJson(extracted)) {
                                        events.add(ServerSentEvent.<String>builder()
                                                .event("metadata")
                                                .data(extracted)
                                                .build());
                                        log.debug("Sent metadata event");
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
                    
                    // Normal chunk without tags
                    contentAccumulator.append(data);
                    events.add(ServerSentEvent.<String>builder()
                            .event("message")
                            .data(data)
                            .build());
                    
                    return Flux.fromIterable(events);
                })
                .concatWith(Flux.defer(() -> {
                    // End of stream: extract suggestions and persist
                    String fullContent = contentAccumulator.toString();
                    
                    log.info("Full AI response received: {} chars", fullContent.length());
                    
                    List<ServerSentEvent<String>> finalEvents = new ArrayList<>();
                    
                    // Extract suggestions
                    String suggestionsJson = null;
                    Matcher suggestionsMatcher = SUGGESTIONS_PATTERN.matcher(fullContent);
                    if (suggestionsMatcher.find()) {
                        suggestionsJson = suggestionsMatcher.group(1).trim();
                        log.info("Suggestions extracted: {}", suggestionsJson);
                        fullContent = suggestionsMatcher.replaceAll("");
                        
                        finalEvents.add(ServerSentEvent.<String>builder()
                                .event("suggestions")
                                .data(suggestionsJson)
                                .build());
                    }
                    
                    String cleanContent = fullContent.trim();
                    log.info("Clean content after tag removal: {} chars", cleanContent.length());
                    
                    // Parse suggestions array for DB storage
                    List<String> suggestionsList = parseSuggestionsArray(suggestionsJson);
                    
                    // Persist to DB
                    persistAiResponse(sessionId, cleanContent, suggestionsList);
                    
                    // Send done event
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
        
        // Decode multiple times in case of double encoding
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

    /**
     * Parse suggestions JSON array into List<String>
     */
    private List<String> parseSuggestionsArray(String suggestionsJson) {
        if (suggestionsJson == null || suggestionsJson.trim().isEmpty()) {
            return null;
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String[] array = mapper.readValue(suggestionsJson, String[].class);
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
            log.error("JSON validation failed: {}", e.getMessage());
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

    /**
     * Persist AI response with suggestions to database
     * Per spec: Store content + suggestions array
     */
    private void persistAiResponse(String sessionId, String content, List<String> suggestions) {
        if (content == null || content.isEmpty()) {
            log.warn("Empty content, skipping persistence");
            return;
        }

        ChatMessage aiMessage = ChatMessage.builder()
                .sessionId(sessionId)
                .sender(Sender.AI)
                .content(content)
                .suggestions(suggestions)
                .timestamp(Instant.now())
                .build();
        
        log.info("Persisting AI message: content={} chars, suggestions={}", 
                content.length(), suggestions != null ? suggestions.size() : 0);
        
        chatMessageRepository.save(aiMessage)
                .flatMap(msg -> updateSessionLastUpdate(sessionId).thenReturn(msg))
                .subscribe(
                    saved -> log.debug("AI message saved: id={}", saved.getId()),
                    error -> log.error("Failed to save AI message", error)
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
