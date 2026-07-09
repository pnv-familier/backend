package com.familier.ai.service;

import com.familier.ai.entity.ChatMessage;
import com.familier.ai.entity.ChatSession;
import com.familier.ai.entity.Sender;
import com.familier.ai.entity.UserContext;
import com.familier.ai.repository.ChatMessageRepository;
import com.familier.ai.repository.ChatSessionRepository;
import com.familier.ai.repository.UserContextRepository;
import com.familier.ai.service.provider.UserProvider;
import com.familier.grpc.UserProfileResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import java.time.Duration;

@Service
@Slf4j
public class SummarizationService {

    private final WebClient webClient;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserContextRepository userContextRepository;
    private final UserProvider userProvider;
    private final ObjectMapper objectMapper;
    private static final Pattern JSON_PATTERN = Pattern.compile("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}");

    @Value("${gemini.api-key}")
    private String API_KEY;

    @Value("${gemini.timeout:120}")
    private long timeoutSeconds;

    @Value("${app.summarization.concurrency:1}")
    private int concurrency;

    @Value("${app.summarization.idle-minutes:5}")
    private int idleMinutes;

    

    public SummarizationService(WebClient.Builder webClientBuilder,
                            ChatSessionRepository chatSessionRepository,
                            ChatMessageRepository chatMessageRepository,
                            UserContextRepository userContextRepository,
                            UserProvider userProvider,
                            ObjectMapper objectMapper) {

    HttpClient httpClient = HttpClient.create()
            .responseTimeout(Duration.ofSeconds(60));

    this.webClient = webClientBuilder
            .baseUrl("https://generativelanguage.googleapis.com")
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();

    this.chatSessionRepository = chatSessionRepository;
    this.chatMessageRepository = chatMessageRepository;
    this.userContextRepository = userContextRepository;
    this.userProvider = userProvider;
    this.objectMapper = objectMapper;
}
    public void summarizeAllOldActiveSessions() {
        java.time.Instant idleThreshold = java.time.Instant.now().minus(idleMinutes, java.time.temporal.ChronoUnit.MINUTES);
        log.info("[Summarization] Triggered — idleThreshold={}, concurrency={}", idleThreshold, concurrency);

        chatSessionRepository.findAllByStatusAndLastUpdateBefore("ACTIVE", idleThreshold)
                .flatMap(session -> {
                    log.info("[Summarization] Queued session={} user={}", session.getId(), session.getUserEmail());
                    return summarizeSession(session.getId(), session.getUserEmail())
                            .doOnError(e -> log.error("Gemini error", e));
                }, concurrency)
                .doOnComplete(() -> log.info("[Summarization] All sessions processed"))
                .subscribe();
    }

    public Mono<Void> summarizeSession(String sessionId, String userEmail) {
        log.info("[Summarization] Starting session={} user={}", sessionId, userEmail);
        return userProvider.getUserProfile(userEmail)
                .flatMap(userProfile -> chatSessionRepository.findById(sessionId)
                        .flatMap(session -> {
                            String currentSummary = session.getSummary() != null ? session.getSummary() : "";
                            Instant lastSummarized = session.getLastSummarizedAt() != null ?
                                    session.getLastSummarizedAt() : session.getCreatedAt();
                            log.debug("[Summarization] session={} lastSummarizedAt={} hasPreviousSummary={}",
                                    sessionId, lastSummarized, !currentSummary.isEmpty());

                            return chatMessageRepository
                                    .findAllBySessionIdAndSenderAndTimestampAfterOrderByTimestampAsc(
                                            sessionId, Sender.USER, lastSummarized)
                                    .collectList()
                                    .flatMap(newMessages -> {
                                        if (newMessages.isEmpty() || newMessages.size() < 2) {
                                            log.info("[Summarization] Skipped session={} — not enough new messages (count={})",
                                                    sessionId, newMessages.size());
                                            return Mono.empty();
                                        }
                                        log.info("[Summarization] Calling Gemini session={} newMessageCount={}",
                                                sessionId, newMessages.size());

                                        String newMessagesText = formatConversation(newMessages);

                                        return callGeminiForSummarization(currentSummary, newMessagesText, userProfile)
                                                .flatMap(result -> updateSessionWithSummary(session, result, userEmail));
                                    });
                        }))
                .then();
    }

    private String formatConversation(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            sb.append("User: ").append(msg.getContent()).append("\n");
            sb.append("AI: [\u0111\u00e3 phản hồi]\n");
        }
        return sb.toString();
    }

    private static final java.util.Set<String> TEMPORARY_KEYS = java.util.Set.of(
            "cam_xuc_hien_tai", "van_de_hien_tai", "mong_muon_ket_noi", "thoi_gian_trong"
    );

    private Mono<SummarizationResult> callGeminiForSummarization(String oldSummary, String newMessages, UserProfileResponse userProfile) {
        String prompt = String.format(
                "Phân tích cuộc trò chuyện sau và trích xuất thông tin về người dùng liên quan đến kết nối gia đình.\n" +
                "Người dùng: %s (%s)\n\n" +
                "TÓM TẮT CŨ: %s\n\n" +
                "TIN NHẮN:\n%s\n\n" +
                "Trả về JSON (KHÔNG thêm markdown):\n" +
                "{\"newSummary\":\"tóm tắt ngắn dưới 80 từ\",\"extractedFacts\":[{\"key\":\"...\",\"value\":\"...\",\"confidence\":0.0,\"category\":\"PERMANENT|TEMPORARY\"}]}\n\n" +
                "KEY HỢP LỆ (chỉ dùng các key này, bỏ qua nếu không có thông tin rõ ràng):\n" +
                "PERMANENT — thông tin lâu dài:\n" +
                "- so_thich: sở thích, hoạt động yêu thích\n" +
                "- tinh_cach: tính cách, thói quen giao tiếp\n" +
                "- quan_he_gia_dinh: thành viên gia đình, mối quan hệ\n" +
                "- so_thich_am_thuc: món ăn, ẩm thực yêu thích\n" +
                "- lich_su_tuong_tac: sự kiện quan trọng đã xảy ra với gia đình (cãi nhau, tặng quà, hòa giải...)\n" +
                "TEMPORARY — thông tin tạm thời, sẽ bị thay thế lần sau:\n" +
                "- cam_xuc_hien_tai: cảm xúc, tâm trạng hiện tại\n" +
                "- van_de_hien_tai: vấn đề đang gặp phải\n" +
                "- mong_muon_ket_noi: mong muốn kết nối với ai, rào cản giao tiếp\n" +
                "- thoi_gian_trong: mốc thời gian rảnh được nhắc đến (ví dụ: rảnh tối thứ 7)\n\n" +
                "QUY TẮC QUAN TRỌNG:\n" +
                "- Mỗi key chỉ xuất hiện ĐÚNG 1 LẦN trong extractedFacts\n" +
                "- Nếu có nhiều giá trị cho cùng 1 key, gộp vào 1 value duy nhất\n" +
                "  Ví dụ: quan_he_gia_dinh: \"có chị gái, có mẹ\" (KHÔNG tách thành 2 facts)\n" +
                "- Chỉ trích xuất nếu confidence >= 0.7. KHÔNG trích xuất: tên, email, ngày sinh, giới tính.",
                userProfile.getFullName(),
                userProfile.getEmail(),
                oldSummary.isEmpty() ? "chưa có" : oldSummary,
                newMessages
        );
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.1,
                        "responseMimeType", "application/json"
                )
        );
        return webClient.post()
                .uri("/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                .map(this::extractTextFromResponse)
                .doOnNext(raw -> log.info("RAW GEMINI: {}", raw))
                .flatMap(raw -> {
                    log.debug("[Summarization] Gemini raw response length={}", raw.length());
                    return parseSummarizationResult(raw);
                })
                .doOnNext(result -> log.info("[Summarization] Gemini parsed — summaryLength={} factsCount={}",
                        result.newSummary.length(), result.facts.size()))
                .doOnError(e -> log.error("Gemini error", e));
    }

    private Mono<SummarizationResult> parseSummarizationResult(String rawResponse) {
        return Mono.fromCallable(() -> {
            String jsonOnly = extractJsonFromResponse(rawResponse);
            
            if (jsonOnly.isEmpty()) {
                throw new RuntimeException("No JSON found in AI response");
            }

            JsonNode root = objectMapper.readTree(jsonOnly);
            
            JsonNode summaryNode = root.get("newSummary");
            if (summaryNode == null || summaryNode.asText().trim().isEmpty()) {
                throw new RuntimeException("AI returned empty summary");
            }
            String newSummary = summaryNode.asText();
            
            List<UserContext.Fact> facts = new ArrayList<>();
            JsonNode factsArray = root.get("extractedFacts");
            if (factsArray != null && factsArray.isArray()) {
                for (JsonNode factNode : factsArray) {
                    String key = factNode.get("key").asText("");
                    String value = factNode.get("value").asText("");
                    double confidence = factNode.get("confidence").asDouble(0.5);
                    
                    if (value.length() > 2 && confidence >= 0.7) {
                        String category = factNode.has("category") ? factNode.get("category").asText("") : "PERMANENT";
                        if (!"TEMPORARY".equals(category)) category = "PERMANENT";
                        UserContext.Fact fact = UserContext.Fact.builder()
                                .key(key)
                                .value(value)
                                .confidence(confidence)
                                .category(category)
                                .updatedAt(Instant.now())
                                .build();
                        facts.add(fact);
                    }
                }
            }
            return new SummarizationResult(newSummary, facts);
        }).doOnError(e -> log.error("Gemini error", e));
    }

    private String extractJsonFromResponse(String rawResponse) {
        if (rawResponse.contains("```json")) {
            try {
                int startIdx = rawResponse.indexOf("```json") + 7;
                int endIdx = rawResponse.indexOf("```", startIdx);
                if (endIdx > startIdx) {
                    return rawResponse.substring(startIdx, endIdx).trim();
                }
            } catch (Exception ignored) {}
        }

        if (rawResponse.contains("```")) {
            try {
                int startIdx = rawResponse.indexOf("```") + 3;
                int endIdx = rawResponse.indexOf("```", startIdx);
                if (endIdx > startIdx) {
                    return rawResponse.substring(startIdx, endIdx).trim();
                }
            } catch (Exception ignored) {}
        }

        Matcher matcher = JSON_PATTERN.matcher(rawResponse);
        if (matcher.find()) {
            return matcher.group();
        }

        try {
            int startIdx = rawResponse.indexOf("{");
            int endIdx = rawResponse.lastIndexOf("}");
            if (startIdx >= 0 && endIdx > startIdx) {
                return rawResponse.substring(startIdx, endIdx + 1);
            }
        } catch (Exception ignored) {}

        return "";
    }

    private Mono<Void> updateSessionAndContext(ChatSession session, String summary, String userEmail, List<UserContext.Fact> facts) {
        if (summary == null || summary.isEmpty()) {
            log.warn("[Summarization] Empty summary returned for session={}, skipping save", session.getId());
            return Mono.empty();
        }

        session.setSummary(summary);
        session.setStatus("COMPLETED");
        session.setLastUpdate(Instant.now());
        session.setLastSummarizedAt(Instant.now());

        return chatSessionRepository.save(session)
                .doOnNext(s -> log.info("[Summarization] Session saved as COMPLETED session={}", s.getId()))
                .flatMap(savedSession -> userContextRepository.findByEmail(userEmail)
                        .defaultIfEmpty(UserContext.builder().email(userEmail).build())
                        .flatMap(context -> {
                            if (context.getId() == null) {
                                log.info("[Summarization] Creating new UserContext for user={} facts={}", userEmail, facts.size());
                                context.setFacts(facts);
                            } else {
                                log.info("[Summarization] Merging facts for user={} newFacts={} existingFacts={}",
                                        userEmail, facts.size(), context.getFacts().size());
                                mergeFacts(context, facts);
                            }
                            return userContextRepository.save(context);
                        }))
                .doOnNext(ctx -> log.info("[Summarization] UserContext saved for user={} totalFacts={}",
                        userEmail, ctx.getFacts().size()))
                .then();
    }

    private void mergeFacts(UserContext context, List<UserContext.Fact> newFacts) {
        List<UserContext.Fact> validFacts = newFacts.stream()
                .filter(f -> f.getValue() != null && f.getValue().length() > 2)
                .filter(f -> f.getConfidence() != null && f.getConfidence() >= 0.7)
                .toList();

        // Bước 1: gộp các facts trùng key trong batch Gemini trả về trước khi merge vào context
        // Đảm bảo nếu Gemini vẫn tách, ta gộp lại trước — không mất data
        java.util.Map<String, UserContext.Fact> deduped = new java.util.LinkedHashMap<>();
        for (UserContext.Fact f : validFacts) {
            if (deduped.containsKey(f.getKey())) {
                UserContext.Fact existing = deduped.get(f.getKey());
                boolean isTemporary = "TEMPORARY".equals(f.getCategory());
                if (isTemporary) {
                    // TEMPORARY: ghi đè hoàn toàn
                    existing.setValue(f.getValue());
                    existing.setConfidence(f.getConfidence());
                } else {
                    // PERMANENT: append nếu value chưa có
                    if (!existing.getValue().contains(f.getValue())) {
                        existing.setValue(existing.getValue() + ", " + f.getValue());
                    }
                    existing.setConfidence(Math.max(existing.getConfidence(), f.getConfidence()));
                }
                existing.setUpdatedAt(Instant.now());
            } else {
                deduped.put(f.getKey(), UserContext.Fact.builder()
                        .key(f.getKey())
                        .value(f.getValue())
                        .confidence(f.getConfidence())
                        .category(f.getCategory())
                        .updatedAt(Instant.now())
                        .build());
            }
        }

        java.util.Set<String> newTemporaryKeys = deduped.values().stream()
                .filter(f -> "TEMPORARY".equals(f.getCategory()))
                .map(UserContext.Fact::getKey)
                .collect(java.util.stream.Collectors.toSet());

        context.getFacts().removeIf(existing ->
                "TEMPORARY".equals(existing.getCategory())
                && TEMPORARY_KEYS.contains(existing.getKey())
                && !newTemporaryKeys.contains(existing.getKey())
        );

        // Bước 3: merge từng fact đã dedup vào context
        for (UserContext.Fact newFact : deduped.values()) {
            boolean isTemporary = "TEMPORARY".equals(newFact.getCategory());
            java.util.Optional<UserContext.Fact> existingInContext = context.getFacts().stream()
                    .filter(f -> f.getKey() != null && f.getKey().equals(newFact.getKey()))
                    .findFirst();

            if (existingInContext.isPresent()) {
                UserContext.Fact e = existingInContext.get();
                if (isTemporary) {
                    // TEMPORARY: luôn ghi đè hoàn toàn
                    e.setValue(newFact.getValue());
                    e.setConfidence(newFact.getConfidence());
                    e.setCategory(newFact.getCategory());
                    e.setUpdatedAt(Instant.now());
                } else {
                    if (!e.getValue().contains(newFact.getValue())) {
                        e.setValue(e.getValue() + ", " + newFact.getValue());
                    }
                    e.setConfidence(Math.max(e.getConfidence(), newFact.getConfidence()));
                    e.setCategory(newFact.getCategory());
                    e.setUpdatedAt(Instant.now());
                }
            } else {
                context.getFacts().add(newFact);
            }
        }
    }

    private Mono<Void> updateSessionWithSummary(ChatSession session, SummarizationResult result, String userEmail) {
        return updateSessionAndContext(session, result.newSummary, userEmail, result.facts);
    }

    private String extractTextFromResponse(Map response) {
        try {
            List<?> candidates = (List<?>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) return "";

            Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
            Map<?, ?> content = (Map<?, ?>) firstCandidate.get("content");
            if (content == null) return "";

            List<?> parts = (List<?>) content.get("parts");
            if (parts == null || parts.isEmpty()) return "";

            Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
            String text = (String) firstPart.get("text");

            return (text != null) ? text : "";
        } catch (Exception e) {
            return "";
        }
    }

    private static class SummarizationResult {
        String newSummary;
        List<UserContext.Fact> facts;

        SummarizationResult(String newSummary, List<UserContext.Fact> facts) {
            this.newSummary = newSummary;
            this.facts = facts;
        }
    }
}
