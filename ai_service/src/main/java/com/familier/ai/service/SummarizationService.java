package com.familier.ai.service;

import com.familier.ai.entity.ChatMessage;
import com.familier.ai.entity.ChatSession;
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

    public SummarizationService(WebClient.Builder webClientBuilder,
                                ChatSessionRepository chatSessionRepository,
                                ChatMessageRepository chatMessageRepository,
                                UserContextRepository userContextRepository,
                                UserProvider userProvider) {
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userContextRepository = userContextRepository;
        this.userProvider = userProvider;
        this.objectMapper = new ObjectMapper();
    }

    public Mono<Void> summarizeSession(String sessionId, String userEmail) {
        return userProvider.getUserProfile(userEmail)
                .flatMap(userProfile -> chatSessionRepository.findById(sessionId)
                        .flatMap(session -> {
                            String currentSummary = session.getSummary() != null ? session.getSummary() : "";
                            Instant lastSummarized = session.getLastSummarizedAt() != null ? 
                                    session.getLastSummarizedAt() : session.getCreatedAt();

                            return chatMessageRepository.findAllBySessionIdOrderByTimestampAsc(sessionId)
                                    .filter(msg -> msg.getTimestamp().isAfter(lastSummarized))
                                    .collectList()
                                    .flatMap(newMessages -> {
                                        if (newMessages.isEmpty()) {
                                            return Mono.empty();
                                        }
                                        
                                        if (newMessages.size() < 3) {
                                            return Mono.empty();
                                        }

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
            sb.append(msg.getSender()).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

    private Mono<SummarizationResult> callGeminiForSummarization(String oldSummary, String newMessages, UserProfileResponse userProfile) {
        String profileInfo = formatProfileInfo(userProfile);
        String prompt = String.format(
                "Bạn đang phân tích một cuộc trò chuyện cho người dùng: %s\n\n" +
                "THÔNG TIN HỒ SƠ (KHÔNG TRÍCH XUẤT CÁI NÀY):\n%s\n\n" +
                "TÓM TẮT CŨ:\n%s\n\n" +
                "TIN NHẮN MỚI:\n%s\n\n" +
                "NHIỆM VỤ: Chỉ trích xuất các SỰ KIỆN CÁ NHÂN MỚI về %s từ cuộc trò chuyện mà KHÔNG có trong hồ sơ của họ.\n" +
                "KHÔNG trích xuất hoặc đề cập: fullName, email, birthday, gender (những cái này đã có trong hồ sơ).\n" +
                "Chỉ trích xuất những hiểu biết và sự kiện được rút ra từ cuộc trò chuyện về người dùng cụ thể này.\n\n" +
                "Trả về một JSON với:\n" +
                "{\n" +
                "  \"newSummary\": \"Tóm tắt cuộc trò chuyện được cập nhật\",\n" +
                "  \"extractedFacts\": [\n" +
                "    {\"key\": \"tên sự kiện\", \"value\": \"giá trị sự kiện\", \"confidence\": 0.8}\n" +
                "  ]\n" +
                "}",
                userProfile.getEmail(),
                profileInfo,
                oldSummary.isEmpty() ? "Không có tóm tắt trước đó" : oldSummary,
                newMessages,
                userProfile.getFullName()
        );

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))));

        return webClient.post()
                .uri("/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                .map(this::extractTextFromResponse)
                .flatMap(this::parseSummarizationResult)
                .onErrorResume(e -> {
                    log.error("Failed to call Gemini for summarization: {}", e.getMessage());
                    return Mono.just(new SummarizationResult("", List.of()));
                });
    }

    private String formatProfileInfo(UserProfileResponse userProfile) {
        StringBuilder sb = new StringBuilder();
        sb.append("Email: ").append(userProfile.getEmail()).append("\n");
        sb.append("Full Name: ").append(userProfile.getFullName()).append("\n");
        sb.append("Birthday: ").append(userProfile.getBirthday()).append("\n");
        sb.append("Gender: ").append(userProfile.getGender()).append("\n");
        return sb.toString();
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
                    
                    if (value.length() > 2 && confidence > 0.6) {
                        UserContext.Fact fact = UserContext.Fact.builder()
                                .key(key)
                                .value(value)
                                .confidence(confidence)
                                .updatedAt(Instant.now())
                                .build();
                        facts.add(fact);
                    }
                }
            }
            return new SummarizationResult(newSummary, facts);
        }).onErrorResume(e -> {
            log.error("Failed to parse summarization result: {}", e.getMessage());
            return Mono.just(new SummarizationResult("", List.of()));
        });
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
        session.setSummary(summary);
        session.setStatus("COMPLETED");
        session.setLastUpdate(Instant.now());
        session.setLastSummarizedAt(Instant.now());

        return chatSessionRepository.save(session)
                .flatMap(savedSession -> userContextRepository.findByEmail(userEmail)
                        .defaultIfEmpty(UserContext.builder().email(userEmail).build())
                        .flatMap(context -> {
                            if (context.getId() == null) {
                                context.setFacts(facts);
                                return userContextRepository.save(context);
                            } else {
                                mergeFacts(context, facts);
                                return userContextRepository.save(context);
                            }
                        }))
                .then();
    }

    private void mergeFacts(UserContext context, List<UserContext.Fact> newFacts) {
        List<UserContext.Fact> validFacts = newFacts.stream()
                .filter(f -> f.getValue() != null && f.getValue().length() > 2)
                .filter(f -> f.getConfidence() > 0.6)
                .toList();
        
        for (UserContext.Fact newFact : validFacts) {
            boolean found = false;
            for (UserContext.Fact existingFact : context.getFacts()) {
                if (existingFact.getKey().equalsIgnoreCase(newFact.getKey())) {
                    if (newFact.getConfidence() >= existingFact.getConfidence() || newFact.getConfidence() > 0.7) {
                        existingFact.setValue(newFact.getValue());
                        existingFact.setConfidence(newFact.getConfidence());
                        existingFact.setUpdatedAt(Instant.now());
                    } else {
                        existingFact.setUpdatedAt(Instant.now());
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                newFact.setUpdatedAt(Instant.now());
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
