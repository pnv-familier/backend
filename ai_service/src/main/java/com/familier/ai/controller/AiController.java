package com.familier.ai.controller;

import com.familier.ai.dto.ChatMessageDto;
import com.familier.ai.dto.FeedbackRequest;
import com.familier.ai.entity.ChatSession;
import com.familier.ai.entity.Report;
import com.familier.ai.service.AiFacadeService;
import com.familier.ai.service.ChatService;
import com.familier.ai.service.ContextManagerService;
import com.familier.ai.service.FakeGeminiService;
import com.familier.ai.service.PromptService;
import com.familier.ai.service.ReportService;
import com.familier.ai.service.SummarizationScheduler;
import com.familier.ai.service.SummarizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AiController {

    private final AiFacadeService aiFacadeService;
    private final PromptService promptService;
    private final ChatService chatService;
    private final ContextManagerService contextManagerService;
    private final SummarizationService summarizationService;
    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    @Autowired
    private FakeGeminiService fakeService;

    @Value("${ai.mock:false}")
    private boolean useMock;

    public AiController(AiFacadeService aiFacadeService,
            PromptService promptService,
            ChatService chatService,
            SummarizationScheduler summarizationScheduler,
            SummarizationService summarizationService,
            ContextManagerService contextManagerService,
            ReportService reportService,
            ObjectMapper objectMapper) {
        this.aiFacadeService = aiFacadeService;
        this.promptService = promptService;
        this.chatService = chatService;
        this.summarizationService = summarizationService;
        this.contextManagerService = contextManagerService;
        this.reportService = reportService;
        this.objectMapper = objectMapper;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chat endpoint
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<ServerSentEvent<String>>>> streamAiResponse(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId,
            @RequestHeader(name = "X-User-Email") String email) throws Exception {

        return chatService.getOrCreateSession(sessionId, message, email)
                .flatMap(session -> {
                    if (useMock) {
                        try {
                            String basicPrompt = promptService.loadSystemPrompt(
                                    "virtual_member_v4", java.util.Map.of());
                            return Mono.just(ResponseEntity.ok()
                                    .header("X-Session-Id", session.getId())
                                    .body(chatService.saveUserMessage(session.getId(), message)
                                            .flatMapMany(saved -> executeAiStream(
                                                    session.getId(), message, basicPrompt, email))));
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    }

                    return contextManagerService.buildSessionContext(email, session.getId())
                            .flatMap(vars -> {
                                try {
                                    String systemPrompt = promptService.loadSystemPrompt(
                                            "virtual_member_v4", vars);
                                    return Mono.just(ResponseEntity.ok()
                                            .header("X-Session-Id", session.getId())
                                            .body(chatService.saveUserMessage(session.getId(), message)
                                                    .flatMapMany(saved -> executeAiStream(
                                                            session.getId(), message, systemPrompt, email))));
                                } catch (Exception e) {
                                    return Mono.error(e);
                                }
                            });
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stream execution + AiStreamProcessor
    // ─────────────────────────────────────────────────────────────────────────

    private Flux<ServerSentEvent<String>> executeAiStream(String sessionId, String message,
            String systemPrompt, String userEmail) {

        AiStreamProcessor processor = new AiStreamProcessor(sessionId, userEmail);

        Flux<ServerSentEvent<String>> aiStream = useMock
                ? fakeService.streamGenerateContent(systemPrompt, message)
                : aiFacadeService.streamChat(systemPrompt, message, userEmail);

        return aiStream
                .concatMap(processor::process)
                .concatWith(Flux.defer(processor::finalizeStream))
                .doOnError(e -> {
                    log.error("Error in AI stream for session {}: {}", sessionId, e.getMessage());
                    chatService.saveAiMessage(sessionId, processor.getCleanContent(), null);
                })
                .onErrorResume(e -> {
                    String errorMessage = "Hiện tại Familier đang gặp vấn đề, vui lòng thử lại sau.";
                    String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    if (errorMsg.contains("model") || errorMsg.contains("gemini")
                            || errorMsg.contains("ai") || errorMsg.contains("generation")) {
                        errorMessage = "Hiện tại Familier đang gặp lỗi với model AI, vui lòng thử lại sau.";
                    }
                    return Flux.just(
                            ServerSentEvent.<String>builder().event("message").data(errorMessage).build(),
                            ServerSentEvent.<String>builder().event("done").data("[DONE]").build());
                });
    }

    /**
     * Stateful processor for AI response streams.
     * Handles <suggestions> quick-reply tag extraction and ensures clean message
     * content is persisted and streamed correctly.
     */
    private class AiStreamProcessor {
        private static final int MAX_BUFFER_SIZE = 100 * 1024; // 100KB guard

        private final StringBuilder cleanContent = new StringBuilder();
        private final StringBuilder tagBuffer = new StringBuilder();
        private final StringBuilder residualBuffer = new StringBuilder();
        private String currentTag = null;
        private String suggestionsJson = null;

        private final String sessionId;
        private final String userEmail;

        public AiStreamProcessor(String sessionId, String userEmail) {
            this.sessionId = sessionId;
            this.userEmail = userEmail;
        }

        public Flux<ServerSentEvent<String>> process(ServerSentEvent<String> event) {
            String data = event.data();
            if (data == null || data.equals("[DONE]") || data.equals("[DONE.]")) {
                return Flux.empty();
            }

            if (residualBuffer.length() + data.length() > MAX_BUFFER_SIZE) {
                log.warn("Buffer limit exceeded for session {}. Flushing as plain text.", sessionId);
                flushBuffersAsPlainText();
            }

            residualBuffer.append(data);
            List<ServerSentEvent<String>> events = new ArrayList<>();

            while (residualBuffer.length() > 0) {
                if (currentTag == null) {
                    if (!findAndProcessTagStart(events)) break;
                } else {
                    if (!findAndProcessTagEnd(events)) break;
                }
            }
            return Flux.fromIterable(events);
        }

        private boolean findAndProcessTagStart(List<ServerSentEvent<String>> events) {
            int suggestionsStart = residualBuffer.indexOf("<suggestions>");

            if (suggestionsStart == -1) {
                int lastOpen = residualBuffer.lastIndexOf("<");
                if (lastOpen != -1) {
                    String potential = residualBuffer.substring(lastOpen);
                    if ("<suggestions>".startsWith(potential)) {
                        emitPlainText(residualBuffer.substring(0, lastOpen), events);
                        residualBuffer.delete(0, lastOpen);
                        return false;
                    }
                }
                emitPlainText(residualBuffer.toString(), events);
                residualBuffer.setLength(0);
                return false;
            } else {
                emitPlainText(residualBuffer.substring(0, suggestionsStart), events);
                currentTag = "suggestions";
                residualBuffer.delete(0, suggestionsStart + "<suggestions>".length());
                return true;
            }
        }

        private boolean findAndProcessTagEnd(List<ServerSentEvent<String>> events) {
            String closingTag = "</suggestions>";
            int closingIdx = residualBuffer.indexOf(closingTag);

            if (closingIdx == -1) {
                int lastOpenSlash = residualBuffer.lastIndexOf("</");
                if (lastOpenSlash != -1) {
                    String potential = residualBuffer.substring(lastOpenSlash);
                    if (closingTag.startsWith(potential)) {
                        tagBuffer.append(residualBuffer.substring(0, lastOpenSlash));
                        residualBuffer.delete(0, lastOpenSlash);
                        return false;
                    }
                }
                tagBuffer.append(residualBuffer);
                residualBuffer.setLength(0);
                return false;
            } else {
                tagBuffer.append(residualBuffer.substring(0, closingIdx));
                suggestionsJson = decodeHtmlEntities(tagBuffer.toString().trim());
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

        public Flux<ServerSentEvent<String>> finalizeStream() {
            if (currentTag != null) {
                log.warn("Stream ended inside <{}> for session {}.", currentTag, sessionId);
                if ("suggestions".equals(currentTag)) {
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
            chatService.saveAiMessage(sessionId, cleanContent.toString().trim(),
                    parseSuggestionsArray(suggestionsJson));

            if (suggestionsJson != null) {
                finalEvents.add(createEvent("suggestions", suggestionsJson));
            }
            finalEvents.add(createEvent("done", "[DONE]"));
            return Flux.fromIterable(finalEvents);
        }

        private void flushBuffersAsPlainText() {
            if ("suggestions".equals(currentTag)) {
                suggestionsJson = tagBuffer.toString();
            }
            tagBuffer.setLength(0);
            currentTag = null;
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

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String decodeHtmlEntities(String text) {
        if (text == null) return null;
        String decoded = text;
        for (int i = 0; i < 3; i++) {
            String temp = decoded
                    .replace("&quot;", "\"").replace("&amp;", "&")
                    .replace("&lt;", "<").replace("&gt;", ">")
                    .replace("&#39;", "'").replace("&#x27;", "'")
                    .replace("&#x2F;", "/");
            if (temp.equals(decoded)) break;
            decoded = temp;
        }
        return decoded;
    }

    private List<String> parseSuggestionsArray(String suggestionsJson) {
        if (suggestionsJson == null || suggestionsJson.trim().isEmpty()) return null;
        try {
            String[] array = objectMapper.readValue(decodeHtmlEntities(suggestionsJson), String[].class);
            return Arrays.asList(array);
        } catch (Exception e) {
            log.error("Failed to parse suggestions JSON: {}", suggestionsJson, e);
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Other endpoints
    // ─────────────────────────────────────────────────────────────────────────

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
        return chatService.getSessionsByUser(email, PageRequest.of(page, size));
    }

    @GetMapping("/history/{sessionId}")
    public Flux<ChatMessageDto> getHistory(
            @PathVariable String sessionId,
            @RequestHeader(name = "X-User-Email") String email) {
        return chatService.getHistory(sessionId, email);
    }

    @PostMapping("/summarization")
    public ResponseEntity<Void> triggerSummarization() {
        summarizationService.summarizeAllOldActiveSessions();
        return ResponseEntity.accepted().build();
    }
}
