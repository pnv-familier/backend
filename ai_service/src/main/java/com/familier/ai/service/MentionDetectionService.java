package com.familier.ai.service;

import com.familier.ai.dto.MentionDetectionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MentionDetectionService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key}")
    private String API_KEY;

    private static final String DETECTION_PROMPT = """
            Phân tích tin nhắn sau và xác định xem người dùng có nhắc đến thành viên gia đình nào không.
            
            Các mối quan hệ có thể có:
            - FATHER (bố, ba, cha, thầy)
            - MOTHER (mẹ, má, mạ)
            - SON (con trai, thằng con, cậu con trai)
            - DAUGHTER (con gái, cô con gái, bé con gái)
            - BROTHER (anh trai, em trai, anh, em)
            - SISTER (chị gái, em gái, chị, em)
            - GRANDFATHER (ông, ông nội, ông ngoại)
            - GRANDMOTHER (bà, bà nội, bà ngoại)
            - SPOUSE (vợ, chồng, người yêu)
            
            Trả về JSON với format:
            {
              "hasMention": true/false,
              "targetRelation": "FATHER" hoặc null,
              "confidence": 0.0-1.0,
              "reasoning": "Giải thích ngắn gọn"
            }
            
            Lưu ý:
            - Chỉ trả về hasMention=true nếu confidence >= 0.7
            - Nếu không chắc chắn, trả về confidence thấp
            - Nếu chỉ hỏi chung chung không rõ người, trả về confidence thấp
            
            Tin nhắn: "{message}"
            """;

    public MentionDetectionService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.objectMapper = objectMapper;
    }

    public Mono<MentionDetectionResult> detectMention(String message) {
        log.debug("Detecting mention in message: {}", message);
        
        String prompt = DETECTION_PROMPT.replace("{message}", message);
        String url = "/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.1,
                        "responseMimeType", "application/json"
                )
        );

        return webClient.post()
                .uri(url)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(this::parseDetectionResponse)
                .doOnNext(result -> log.info("Mention detection result: hasMention={}, relation={}, confidence={}", 
                        result.isHasMention(), result.getTargetRelation(), result.getConfidence()))
                .onErrorReturn(MentionDetectionResult.builder()
                        .hasMention(false)
                        .confidence(0.0)
                        .reasoning("Error during detection")
                        .build())
                .doOnError(e -> log.error("Error detecting mention", e));
    }

    private MentionDetectionResult parseDetectionResponse(Map<String, Object> response) {
        try {
            List<?> candidates = (List<?>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return createDefaultResult();
            }

            Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
            Map<?, ?> content = (Map<?, ?>) firstCandidate.get("content");
            if (content == null) {
                return createDefaultResult();
            }

            List<?> parts = (List<?>) content.get("parts");
            if (parts == null || parts.isEmpty()) {
                return createDefaultResult();
            }

            Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
            String text = (String) firstPart.get("text");
            
            if (text == null || text.isEmpty()) {
                return createDefaultResult();
            }

            // Parse JSON response from Gemini
            JsonNode jsonNode = objectMapper.readTree(text);
            
            boolean hasMention = jsonNode.has("hasMention") && jsonNode.get("hasMention").asBoolean();
            String targetRelation = jsonNode.has("targetRelation") && !jsonNode.get("targetRelation").isNull() 
                    ? jsonNode.get("targetRelation").asText() 
                    : null;
            double confidence = jsonNode.has("confidence") ? jsonNode.get("confidence").asDouble() : 0.0;
            String reasoning = jsonNode.has("reasoning") ? jsonNode.get("reasoning").asText() : "";

            // Apply confidence threshold
            if (confidence < 0.7) {
                hasMention = false;
                targetRelation = null;
            }

            return MentionDetectionResult.builder()
                    .hasMention(hasMention)
                    .targetRelation(targetRelation)
                    .confidence(confidence)
                    .reasoning(reasoning)
                    .build();

        } catch (Exception e) {
            log.error("Error parsing detection response", e);
            return createDefaultResult();
        }
    }

    private MentionDetectionResult createDefaultResult() {
        return MentionDetectionResult.builder()
                .hasMention(false)
                .confidence(0.0)
                .reasoning("Unable to parse response")
                .build();
    }
}
