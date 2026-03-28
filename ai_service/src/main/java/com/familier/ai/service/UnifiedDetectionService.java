package com.familier.ai.service;

import com.familier.ai.dto.UnifiedDetectionResult;
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
public class UnifiedDetectionService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key}")
    private String API_KEY;

    private static final String UNIFIED_DETECTION_PROMPT = """
            Phân tích tin nhắn sau và xác định 2 điều:
            
            1. MENTION DETECTION - Người dùng có nhắc đến thành viên gia đình nào không?
            Các mối quan hệ: FATHER, MOTHER, SON, DAUGHTER, BROTHER, SISTER, GRANDFATHER, GRANDMOTHER, SPOUSE
            
            2. SUGGESTION DETECTION - Tin nhắn có chứa ý định cần hành động không?
            - EVENT: Nhắc đến thời gian cụ thể (HH:mm), ngày tháng, sự kiện, địa điểm
            - TASK: Nhắc đến chăm sóc, sức khỏe, công việc cho thành viên cụ thể
            - OFFLINE: Thể hiện cảm xúc mạnh hoặc cần kết nối cá nhân
              Sub-types của OFFLINE (chọn 1):
              * EMOTIONAL_SUPPORT: mệt mỏi, áp lực, bế tắc, muốn bỏ cuộc
              * SOCIAL_ISOLATION: một mình, không ai hiểu, nhớ nhà, cô đơn
              * POSITIVE_MILESTONE: vừa được khen, đạt giải, xong dự án, vui quá
              * STRONG_NEGATIVE_EMOTION: tức giận, bực mình, vô lý
            
            QUY TẮC ĐẶC BIỆT:
            - Nếu tin nhắn có từ: 'đề xuất', 'gợi ý', 'tạo lịch', 'nhắc mình' -> suggestion.confidence = 1.0
            - Nếu phát hiện cảm xúc (buồn, stress, mệt mỏi, ...) hoặc không biết kết nối với gia đình -> cho loại 'OFFLINE' -> suggestion.confidence >= 0.5
            
            Trả về JSON:
            {
              "mention": {
                "hasMention": true/false,
                "targetRelation": "FATHER" hoặc null,
                "confidence": 0.0-1.0
              },
              "suggestion": {
                "hasSuggestion": true/false,
                "type": "EVENT"|"TASK"|"OFFLINE" hoặc null,
                "subType": "EMOTIONAL_SUPPORT"|"SOCIAL_ISOLATION"|"POSITIVE_MILESTONE"|"STRONG_NEGATIVE_EMOTION" hoặc null,
                "confidence": 0.0-1.0
              }
            }
            
            Lưu ý:
            - Chỉ trả về hasMention=true nếu confidence >= 0.6
            - Chỉ trả về hasSuggestion=true nếu: (type != 'OFFLINE' và confidence >= 0.6) HOẶC (type == 'OFFLINE' và confidence >= 0.5)
            - subType chỉ có giá trị khi type = OFFLINE
            - Phân loại type phải nghiêm ngặt
            
            Tin nhắn: "{message}"
            """;
    
    public UnifiedDetectionService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl("https://generativelanguage.googleapis.com").build();
        this.objectMapper = objectMapper;
    }

    public Mono<UnifiedDetectionResult> detectUnified(String message) {
        log.debug("Unified detection for message: {}", message);
        
        String prompt = UNIFIED_DETECTION_PROMPT.replace("{message}", message);
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
                .timeout(java.time.Duration.ofSeconds(60))
                .map(response -> parseUnifiedResponse(response, message))
                .doOnNext(result -> log.info("Detection result: mention={}, suggestion={}", 
                        result.getMention().isHasMention(), result.getSuggestion().isHasSuggestion()))
                .onErrorReturn(createDefaultResult())
                .doOnError(e -> log.error("Error in unified detection", e));
    }

    private UnifiedDetectionResult parseUnifiedResponse(Map<String, Object> response, String originalMessage) {
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

            JsonNode root = objectMapper.readTree(text);
            
            // Parse mention
            JsonNode mentionNode = root.get("mention");
            UnifiedDetectionResult.MentionDetection mention = UnifiedDetectionResult.MentionDetection.builder()
                    .hasMention(mentionNode.has("hasMention") && mentionNode.get("hasMention").asBoolean())
                    .targetRelation(mentionNode.has("targetRelation") && !mentionNode.get("targetRelation").isNull() 
                            ? mentionNode.get("targetRelation").asText() : null)
                    .confidence(mentionNode.has("confidence") ? mentionNode.get("confidence").asDouble() : 0.0)
                    .build();

            if (mention.getConfidence() < 0.7) {
                mention.setHasMention(false);
                mention.setTargetRelation(null);
            }

            // Parse suggestion
            JsonNode suggestionNode = root.get("suggestion");
            String type = suggestionNode.has("type") && !suggestionNode.get("type").isNull() 
                    ? suggestionNode.get("type").asText() : null;
            double confidence = suggestionNode.has("confidence") ? suggestionNode.get("confidence").asDouble() : 0.0;

            // Apply priority rules for keywords
            String lowerMessage = originalMessage.toLowerCase();
            if (lowerMessage.contains("đề xuất") || lowerMessage.contains("gợi ý") 
                    || lowerMessage.contains("tạo lịch") || lowerMessage.contains("nhắc mình")) {
                confidence = 1.0;
            }

            UnifiedDetectionResult.SuggestionDetection suggestion = UnifiedDetectionResult.SuggestionDetection.builder()
                    .hasSuggestion(suggestionNode.has("hasSuggestion") && suggestionNode.get("hasSuggestion").asBoolean())
                    .type(type)
                    .subType(suggestionNode.has("subType") && !suggestionNode.get("subType").isNull()
                            ? suggestionNode.get("subType").asText() : null)
                    .confidence(confidence)
                    .build();

            double threshold = "OFFLINE".equals(type) ? 0.5 : 0.6;
            if (suggestion.getConfidence() < threshold) {
                suggestion.setHasSuggestion(false);
                suggestion.setType(null);
                suggestion.setSubType(null);
            }

            // isBroadcast = true khi OFFLINE và có subType hợp lệ
            boolean isBroadcast = "OFFLINE".equals(suggestion.getType())
                    && suggestion.getSubType() != null
                    && suggestion.isHasSuggestion();
            suggestion.setBroadcast(isBroadcast);

            return UnifiedDetectionResult.builder()
                    .mention(mention)
                    .suggestion(suggestion)
                    .build();

        } catch (Exception e) {
            log.error("Error parsing unified detection response", e);
            return createDefaultResult();
        }
    }

    private UnifiedDetectionResult createDefaultResult() {
        return UnifiedDetectionResult.builder()
                .mention(UnifiedDetectionResult.MentionDetection.builder()
                        .hasMention(false)
                        .confidence(0.0)
                        .build())
                .suggestion(UnifiedDetectionResult.SuggestionDetection.builder()
                        .hasSuggestion(false)
                        .confidence(0.0)
                        .build())
                .build();
    }
}
