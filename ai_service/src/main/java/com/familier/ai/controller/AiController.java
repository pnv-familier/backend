package com.familier.ai.controller;

import com.familier.ai.dto.ChatMessageDto;
import com.familier.ai.dto.FamilyMembersDto;
import com.familier.ai.dto.FeedbackRequest;
import com.familier.ai.service.ReportService;
import com.familier.ai.entity.ChatMessage;
import com.familier.ai.entity.ChatSession;
import com.familier.ai.entity.Sender;
import com.familier.ai.repository.ChatMessageRepository;
import com.familier.ai.repository.ChatSessionRepository;
import com.familier.ai.service.ContextManagerService;
import com.familier.ai.service.FakeGeminiService;
import com.familier.ai.service.GeminiService;
import com.familier.ai.service.PromptService;
import com.familier.ai.service.SuggestionService;
import com.familier.ai.service.SummarizationScheduler;
import com.familier.ai.service.SummarizationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.familier.ai.entity.Report;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AiController {

    private final GeminiService geminiService;
    private final PromptService promptService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ContextManagerService contextManagerService;
    private final SummarizationService summarizationService;
    private final SuggestionService suggestionService;
    private final ReportService reportService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final String coreServiceUrl;
    private final String internalSecret;

    @Autowired
    private FakeGeminiService fakeService;

    @Value("${ai.mock:false}")
    private boolean useMock;

    public AiController(GeminiService geminiService,
            PromptService promptService,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            SummarizationScheduler summarizationScheduler,
            SummarizationService summarizationService,
            ContextManagerService contextManagerService,
            SuggestionService suggestionService,
            ReportService reportService,
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${CORE_SERVICE_URL}") String coreServiceUrl,
            @Value("${application.security.internal.secret}") String internalSecret) {
        this.geminiService = geminiService;
        this.promptService = promptService;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.summarizationService = summarizationService;
        this.contextManagerService = contextManagerService;
        this.suggestionService = suggestionService;
        this.reportService = reportService;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
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
                .flatMap(session -> {
                    if (useMock) {
                        // Skip detection when ai.fake=true
                        try {
                            String basicPrompt = promptService.loadSystemPrompt("virtual_member_v3", 
                                    java.util.Map.of(), false, null);
                            return Mono.just(ResponseEntity.ok()
                                    .header("X-Session-Id", session.getId())
                                    .body(saveUserMessage(session.getId(), message)
                                            .flatMapMany(savedMsg -> executeAiStream(session.getId(), message,
                                                    basicPrompt, null, null, email, false, null))));
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    }
                    
                    return contextManagerService
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

                                    boolean shouldBroadcast = result.getDetection().getSuggestion().isBroadcast();
                                    String subType = result.getDetection().getSuggestion().getSubType();

                                    return Mono.just(ResponseEntity.ok()
                                            .header("X-Session-Id", session.getId())
                                            .body(saveUserMessage(session.getId(), message)
                                                    .flatMapMany(savedMsg -> executeAiStream(session.getId(), message,
                                                            enrichedPrompt,
                                                            result.getTargetUserEmail(), suggestionType, email,
                                                            shouldBroadcast, subType))));
                                } catch (Exception e) {
                                    return Mono.error(e);
                                }
                            });
                });
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
            String targetUserEmail, String suggestionType, String userEmail,
            boolean shouldBroadcast, String subType) {

        AiStreamProcessor processor = new AiStreamProcessor(sessionId, suggestionType, targetUserEmail, userEmail);

        Flux<ServerSentEvent<String>> aiStream = useMock
                ? fakeService.streamGenerateContent(systemPrompt, message)
                : geminiService.streamGenerateContent(systemPrompt, message);

        return aiStream
                .concatMap(processor::process)
                .concatWith(Flux.defer(processor::finalizeStream))
                .doOnComplete(() -> {
                    // Fire-and-forget broadcast — không block stream chính
                    if (shouldBroadcast && subType != null) {
                        broadcastToFamily(userEmail, message, subType);
                    }
                })
                .doOnError(e -> {
                    log.error("Error in AI stream for session {}: {}", sessionId, e.getMessage());
                    persistAiResponse(sessionId, processor.getCleanContent(), null);
                })
                .onErrorResume(e -> {
                    String errorMessage = "Hiện tại Familier đang gặp vấn đề, vui lòng thử lại sau.";
                    String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    if (errorMsg.contains("model") || errorMsg.contains("gemini") ||
                            errorMsg.contains("ai") || errorMsg.contains("generation")) {
                        errorMessage = "Hiện tại Familier đang gặp lỗi với model AI, vui lòng thử lại sau.";
                    }
                    return Flux.just(
                            ServerSentEvent.<String>builder().event("message").data(errorMessage).build(),
                            ServerSentEvent.<String>builder().event("done").data("[DONE]").build());
                });
    }

    /**
     * Stateful processor for AI response streams.
     * Handles tag extraction (<suggestion_metadata>, <suggestions>) and ensures
     * clean message content is persisted and streamed correctly.
     */
    private class AiStreamProcessor {
        private static final int MAX_BUFFER_SIZE = 100 * 1024; // 100KB protection

        private final StringBuilder cleanContent = new StringBuilder();
        private final StringBuilder tagBuffer = new StringBuilder();
        private final StringBuilder residualBuffer = new StringBuilder();
        private String currentTag = null; // "metadata" or "suggestions"
        private String suggestionsJson = null;
        private String processedMetadataJson = null;

        private final String sessionId;
        private final String suggestionType;
        private final String targetUserEmail;
        private final String userEmail;

        public AiStreamProcessor(String sessionId, String suggestionType, String targetUserEmail, String userEmail) {
            this.sessionId = sessionId;
            this.suggestionType = suggestionType;
            this.targetUserEmail = targetUserEmail;
            this.userEmail = userEmail;
        }

        /**
         * Processes each chunk of data from the AI stream.
         * Non-blocking and state-safe when used with concatMap.
         */
        public Flux<ServerSentEvent<String>> process(ServerSentEvent<String> event) {
            String data = event.data();
            // Standardize DONE signal handling
            if (data == null || data.equals("[DONE]") || data.equals("[DONE.]")) {
                return Flux.empty();
            }

            // Prevent unbounded memory growth
            if (residualBuffer.length() + data.length() > MAX_BUFFER_SIZE) {
                log.warn("Buffer limit exceeded for session {}. Flushing as plain text.", sessionId);
                flushBuffersAsPlainText();
            }

            residualBuffer.append(data);
            List<ServerSentEvent<String>> events = new ArrayList<>();

            // Main parsing loop: process residual buffer until no more complete tokens are found
            while (residualBuffer.length() > 0) {
                if (currentTag == null) {
                    if (!findAndProcessTagStart(events)) {
                        break; // No more tags or partial tag start detected
                    }
                } else {
                    if (!findAndProcessTagEnd(events)) {
                        break; // Closing tag not found yet or partial closing tag detected
                    }
                }
            }
            return Flux.fromIterable(events);
        }

        private boolean findAndProcessTagStart(List<ServerSentEvent<String>> events) {
            int metadataStart = residualBuffer.indexOf("<suggestion_metadata>");
            int suggestionsStart = residualBuffer.indexOf("<suggestions>");
            
            int tagStartIdx = -1;
            String tagType = null;
            int tagLength = 0;

            if (metadataStart != -1 && (suggestionsStart == -1 || metadataStart < suggestionsStart)) {
                tagStartIdx = metadataStart;
                tagType = "metadata";
                tagLength = "<suggestion_metadata>".length();
            } else if (suggestionsStart != -1) {
                tagStartIdx = suggestionsStart;
                tagType = "suggestions";
                tagLength = "<suggestions>".length();
            }

            if (tagStartIdx == -1) {
                // Check for partial tag starts (e.g., "<sugge") to avoid emitting them prematurely
                int lastOpenBracket = residualBuffer.lastIndexOf("<");
                if (lastOpenBracket != -1) {
                    String potential = residualBuffer.substring(lastOpenBracket);
                    if ("<suggestion_metadata>".startsWith(potential) || "<suggestions>".startsWith(potential)) {
                        emitPlainText(residualBuffer.substring(0, lastOpenBracket), events);
                        residualBuffer.delete(0, lastOpenBracket);
                        return false; 
                    }
                }
                // No tag found, emit everything as plain text
                emitPlainText(residualBuffer.toString(), events);
                residualBuffer.setLength(0);
                return false;
            } else {
                // Full tag start found
                emitPlainText(residualBuffer.substring(0, tagStartIdx), events);
                currentTag = tagType;
                residualBuffer.delete(0, tagStartIdx + tagLength);
                return true;
            }
        }

        private boolean findAndProcessTagEnd(List<ServerSentEvent<String>> events) {
            String closingTag = currentTag.equals("metadata") ? "</suggestion_metadata>" : "</suggestions>";
            int closingIdx = residualBuffer.indexOf(closingTag);

            if (closingIdx == -1) {
                // Check for partial closing tags (e.g., "</sugge")
                int lastOpenSlash = residualBuffer.lastIndexOf("</");
                if (lastOpenSlash != -1) {
                    String potential = residualBuffer.substring(lastOpenSlash);
                    if (closingTag.startsWith(potential)) {
                        tagBuffer.append(residualBuffer.substring(0, lastOpenSlash));
                        residualBuffer.delete(0, lastOpenSlash);
                        return false;
                    }
                }
                // Still inside tag, buffer current chunk
                tagBuffer.append(residualBuffer);
                residualBuffer.setLength(0);
                return false;
            } else {
                // Found closing tag
                tagBuffer.append(residualBuffer.substring(0, closingIdx));
                handleTagContent(currentTag, tagBuffer.toString().trim(), events);
                
                residualBuffer.delete(0, closingIdx + closingTag.length());
                tagBuffer.setLength(0);
                currentTag = null;
                return true;
            }
        }

        private void emitPlainText(String text, List<ServerSentEvent<String>> events) {
            if (text != null && !text.isEmpty()) {
                cleanContent.append(text);
                events.add(createEvent("message", text));
            }
        }

        private void handleTagContent(String tagType, String extracted, List<ServerSentEvent<String>> events) {
            String decoded = decodeHtmlEntities(extracted);
            if ("metadata".equals(tagType)) {
                String processed = decoded;
                if (suggestionType != null) {
                    processed = injectTypeField(processed, suggestionType);
                }
                if ("TASK".equals(suggestionType) && targetUserEmail != null && !targetUserEmail.isEmpty()) {
                    processed = injectAssigneeEmail(processed, targetUserEmail);
                }
                processedMetadataJson = processed;
                
                if (isValidJson(processed) && !"TASK".equals(suggestionType)) {
                    events.add(createEvent("metadata", processed));
                }
            } else if ("suggestions".equals(tagType)) {
                suggestionsJson = decoded;
                // Don't append suggestions to cleanContent anymore - keep them separate
            }
        }

        /**
         * Standardizes the end of the stream, flushing buffers and persisting results.
         */
        public Flux<ServerSentEvent<String>> finalizeStream() {
            // Handle incomplete tags: flush them as plain text to avoid data loss
            if (currentTag != null) {
                log.warn("Stream ended while inside tag <{}> for session {}.", currentTag, sessionId);
                if ("metadata".equals(currentTag)) {
                    String tagPrefix = "<suggestion_metadata>";
                    cleanContent.append(tagPrefix).append(tagBuffer);
                } else if ("suggestions".equals(currentTag)) {
                    // Don't add incomplete suggestions to cleanContent
                    // Try to capture what we have for suggestions field
                    suggestionsJson = tagBuffer.toString();
                }
                tagBuffer.setLength(0);
                currentTag = null;
            }

            if (residualBuffer.length() > 0) {
                cleanContent.append(residualBuffer);
                residualBuffer.setLength(0);
            }

            List<ServerSentEvent<String>> finalEvents = new ArrayList<>();
            persistAiResponse(sessionId, cleanContent.toString().trim(), parseSuggestionsArray(suggestionsJson));
            
            processTaskCreation();
            
            if (suggestionsJson != null) {
                finalEvents.add(createEvent("suggestions", suggestionsJson));
            }
            
            finalEvents.add(createEvent("done", "[DONE]"));
            return Flux.fromIterable(finalEvents);
        }

        private void processTaskCreation() {
            if ("TASK".equals(suggestionType) && processedMetadataJson != null) {
                try {
                    Object metadata = objectMapper.readValue(processedMetadataJson, Object.class);
                    createSuggestionsForFamilyMembers(userEmail, suggestionType, metadata);
                } catch (Exception e) {
                    log.error("Failed to parse task metadata for session {}", sessionId, e);
                }
            }
        }

        private void flushBuffersAsPlainText() {
            if (currentTag != null) {
                if ("metadata".equals(currentTag)) {
                    String tagPrefix = "<suggestion_metadata>";
                    cleanContent.append(tagPrefix).append(tagBuffer);
                } else if ("suggestions".equals(currentTag)) {
                    // Don't add suggestions to cleanContent - they should be separate
                    // Just capture the content for suggestions field
                    suggestionsJson = tagBuffer.toString();
                }
                tagBuffer.setLength(0);
                currentTag = null;
            }
            cleanContent.append(residualBuffer);
            residualBuffer.setLength(0);
        }

        public String getCleanContent() {
            return cleanContent.toString().trim();
        }

        private ServerSentEvent<String> createEvent(String name, String data) {
            return ServerSentEvent.<String>builder().event(name).data(data).build();
        }
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
            String[] array = objectMapper.readValue(decoded, String[].class);
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
            objectMapper.readTree(json);
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

    private void broadcastToFamily(String userEmail, String originalMessage, String subType) {
        // Static mapping subType -> emotion/context — không gọi Gemini thêm
        String emotion = switch (subType) {
            case "EMOTIONAL_SUPPORT" -> "mệt mỏi và cần được động viên";
            case "SOCIAL_ISOLATION" -> "cảm thấy cô đơn";
            case "POSITIVE_MILESTONE" -> "vừa đạt được điều gì đó đáng vui";
            case "STRONG_NEGATIVE_EMOTION" -> "đang có cảm xúc tiêu cực";
            default -> "cần sự quan tâm";
        };

        // Extract context từ message (lấy 50 ký tự đầu làm context ngắn)
        String context = originalMessage.length() > 50
                ? originalMessage.substring(0, 50) + "..."
                : originalMessage;

        java.util.Map<String, String> payload = java.util.Map.of(
                "senderEmail", userEmail,
                "senderName", userEmail, // core service sẽ resolve tên từ email
                "emotion", emotion,
                "context", context,
                "subType", subType
        );

        WebClient webClient = webClientBuilder.baseUrl(coreServiceUrl).build();
        webClient.post()
                .uri("/api/v1/suggestions/broadcast")
                .header("X-Internal-Secret", internalSecret)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Void.class)
                .subscribe(
                        v -> log.info("[Broadcast] Sent for user={} subType={}", userEmail, subType),
                        e -> log.error("[Broadcast] Failed for user={}: {}", userEmail, e.getMessage())
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

    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<Report>> processFeedback(
            @Valid @RequestBody FeedbackRequest request,
            @RequestHeader("X-User-Email") String email) {
        return reportService.processFeedback(request, email)
                .map(report -> ResponseEntity.status(HttpStatus.CREATED).body(report));
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

    @PostMapping("/summarization")
    public ResponseEntity<Void> triggerSummarization() {
        summarizationService.summarizeAllOldActiveSessions();
        return ResponseEntity.accepted().build();
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
