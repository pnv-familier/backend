package com.familier.ai.service;

import com.familier.ai.dto.TargetProfileWithRelation;
import com.familier.ai.entity.UserContext;
import com.familier.ai.repository.ChatMessageRepository;
import com.familier.ai.repository.ChatSessionRepository;
import com.familier.ai.repository.UserContextRepository;
import com.familier.ai.service.provider.UserProvider;
import com.familier.grpc.UserProfileResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ContextManagerService {

    private final UserContextRepository userContextRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MentionDetectionService mentionDetectionService;
    private final TargetProfileService targetProfileService;
    private final RelationMappingService relationMappingService;
    private final UserProvider userProvider;
    private final ObjectMapper objectMapper;

    public ContextManagerService(UserContextRepository userContextRepository,
                                 ChatSessionRepository chatSessionRepository,
                                 ChatMessageRepository chatMessageRepository,
                                 MentionDetectionService mentionDetectionService,
                                 TargetProfileService targetProfileService,
                                 RelationMappingService relationMappingService,
                                 UserProvider userProvider,
                                 ObjectMapper objectMapper) {
        this.userContextRepository = userContextRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.mentionDetectionService = mentionDetectionService;
        this.targetProfileService = targetProfileService;
        this.relationMappingService = relationMappingService;
        this.userProvider = userProvider;
        this.objectMapper = objectMapper;
    }

    public Mono<Map<String, String>> buildVariables(String email, String sessionId, String message, String taggedUserEmail) {
        return Mono.zip(
                userProvider.getUserProfile(email),
                getUserContext(email),
                getSessionSummary(sessionId),
                getRecentMessages(sessionId, 5),
                resolveTargetUser(email, message, taggedUserEmail)
        ).flatMap(tuple -> {
            UserProfileResponse userProfile = tuple.getT1();
            UserContext userContext = tuple.getT2();
            String summary = tuple.getT3();
            String recentMessages = tuple.getT4();
            TargetUserResolution targetResolution = tuple.getT5();
            
            Map<String, Object> currentUserProfile = convertUserProfileResponseToMap(userProfile);
            Map<String, String> variables = new HashMap<>();
            
            String userContextValue = formatCurrentUserContext(currentUserProfile);
            variables.put("USER_CONTEXT", userContextValue);
            
            String globalContext = userContext.getGlobalContext() != null && !userContext.getGlobalContext().isEmpty()
                    ? userContext.getGlobalContext()
                    : "Chưa có dữ liệu";
            variables.put("globalContext", globalContext);
            
            String facts = buildFactsList(userContext);
            variables.put("facts", facts);
            
            String summaryValue = summary != null && !summary.isEmpty()
                    ? summary
                    : "Chưa có tóm tắt";
            variables.put("summary", summaryValue);
            
            variables.put("RECENT_MESSAGES", recentMessages);
            
            if (targetResolution.hasTarget()) {
                return buildTargetVariables(targetResolution.getTargetEmail(), email)
                        .map(targetVars -> {
                            variables.putAll(targetVars);
                            return variables;
                        });
            } else {
                variables.put("MEMBER_REFERENCE_PROMPT", "");
                return Mono.just(variables);
            }
        });
    }

    private Map<String, Object> convertUserProfileResponseToMap(UserProfileResponse userProfile) {
        Map<String, Object> map = new HashMap<>();
        map.put("email", userProfile.getEmail());
        map.put("fullName", userProfile.getFullName());
        map.put("hobbies", userProfile.getHobbiesJson());
        map.put("birthday", userProfile.getBirthday());
        map.put("gender", userProfile.getGender());
        return map;
    }

    private Mono<TargetUserResolution> resolveTargetUser(String currentUserEmail, String message, String taggedUserEmail) {
        if (taggedUserEmail != null && !taggedUserEmail.isEmpty()) {
            return Mono.just(new TargetUserResolution(true, taggedUserEmail, 1.0));
        }
        
        return mentionDetectionService.detectMention(message)
                .flatMap(detection -> {
                    if (detection.isHasMention() && detection.getConfidence() >= 0.7) {
                        return relationMappingService.mapRelationToEmail(currentUserEmail, detection.getTargetRelation())
                                .filter(targetEmail -> !targetEmail.isEmpty())
                                .map(targetEmail -> {
                                    log.info("Successfully mapped {} to email: {}", detection.getTargetRelation(), targetEmail);
                                    return new TargetUserResolution(true, targetEmail, detection.getConfidence());
                                })
                                .defaultIfEmpty(new TargetUserResolution(false, null, detection.getConfidence()));
                    }
                    return Mono.just(new TargetUserResolution(false, null, detection.getConfidence()));
                })
                .onErrorResume(e -> {
                    log.error("Error in mention detection", e);
                    return Mono.just(new TargetUserResolution(false, null, 0.0));
                });
    }

    private Mono<Map<String, String>> buildTargetVariables(String targetEmail, String currentUserEmail) {
        return Mono.zip(
                targetProfileService.getTargetProfile(currentUserEmail, targetEmail)
                        .doOnError(e -> log.error("Failed to fetch target profile for {}: {}", targetEmail, e.getMessage())),
                getUserContext(targetEmail)
                        .doOnError(e -> log.error("Failed to fetch target context for {}: {}", targetEmail, e.getMessage()))
        ).map(tuple -> {
            TargetProfileWithRelation targetProfile = tuple.getT1();
            UserContext targetContext = tuple.getT2();
            
            Map<String, String> targetVars = new HashMap<>();
            
            String targetProfileStr = formatTargetProfile(targetProfile);
            targetVars.put("TARGET_PROFILE_WITH_RELATION", targetProfileStr);
            
            String targetFacts = buildFactsList(targetContext);
            targetVars.put("TARGET_CONTEXT", targetFacts);
            
            String memberPrompt = buildMemberReferencePrompt(targetProfile, targetFacts);
            targetVars.put("MEMBER_REFERENCE_PROMPT", memberPrompt);
            
            log.info("Successfully built target variables for email: {}", targetEmail);
            return targetVars;
        }).onErrorResume(e -> {
            log.error("Error building target variables: {}", e.getMessage());
            return Mono.just(Map.of("MEMBER_REFERENCE_PROMPT", ""));
        });
    }

    private String formatTargetProfile(TargetProfileWithRelation profile) {
        if (profile == null || profile.getEmail() == null) {
            return "Không có thông tin";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Tên: ").append(profile.getFullName() != null ? profile.getFullName() : "N/A").append("\n");
        sb.append("Quan hệ: ").append(mapRelationToVietnamese(profile.getRelationType())).append("\n");
        sb.append("Sinh nhật: ").append(profile.getBirthday() != null ? profile.getBirthday() : "N/A").append("\n");
        sb.append("Giới tính: ").append(profile.getGender() != null ? profile.getGender() : "N/A");
        return sb.toString();
    }

    private String formatCurrentUserContext(Map<String, Object> profile) {
        if (profile == null || profile.isEmpty()) {
            return "Không có thông tin";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Email: ").append(profile.getOrDefault("email", "N/A")).append("\n");
        sb.append("Full Name: ").append(profile.getOrDefault("fullName", "N/A")).append("\n");
        sb.append("Birthday: ").append(profile.getOrDefault("birthday", "N/A")).append("\n");
        sb.append("Gender: ").append(profile.getOrDefault("gender", "N/A")).append("\n");
        String hobbiesFormatted = parseAndFormatHobbies((String) profile.get("hobbies"));
        sb.append("Sở thích: \n").append(hobbiesFormatted);
        return sb.toString();
    }

    private String mapRelationToVietnamese(String relationType) {
        if (relationType == null) return "N/A";
        
        return switch (relationType) {
            case "SELF" -> "Chính mình";
            case "FATHER" -> "Bố";
            case "MOTHER" -> "Mẹ";
            case "SON" -> "Con trai";
            case "DAUGHTER" -> "Con gái";
            case "BROTHER" -> "Anh/Em trai";
            case "SISTER" -> "Chị/Em gái";
            case "GRANDFATHER" -> "Ông";
            case "GRANDMOTHER" -> "Bà";
            case "SPOUSE" -> "Vợ/Chồng";
            default -> relationType;
        };
    }

    private String buildMemberReferencePrompt(TargetProfileWithRelation profile, String targetFacts) {
        if (profile == null || profile.getEmail() == null) {
            return "";
        }
        
        String relation = mapRelationToVietnamese(profile.getRelationType());
        String name = profile.getFullName() != null ? profile.getFullName() : "thành viên này";
        String hobbiesFormatted = parseAndFormatHobbies(profile.getHobbies());
        
        return String.format("""
                # THÔNG TIN THÀNH VIÊN ĐƯỢC NHẮC ĐẾN
                Người dùng đang hỏi về %s (%s).
                
                [THÔNG TIN CƠ BẢN]:
                %s
                
                [SỞ THÍCH]:
                %s
                
                [SỰ THẬT VỀ THÀNH VIÊN NÀY]:
                %s
                
                Hãy sử dụng thông tin này để trả lời câu hỏi của người dùng một cách chính xác và thấu cảm.
                """, relation, name, formatTargetProfile(profile), hobbiesFormatted, targetFacts);
    }

    private Mono<UserContext> getUserContext(String email) {
        return userContextRepository.findByEmail(email)
                .defaultIfEmpty(UserContext.builder()
                        .email(email)
                        .globalContext("Chưa có dữ liệu")
                        .build())
                .onErrorResume(e -> {
                    log.error("Failed to fetch user context for email: {}", email, e);
                    return Mono.just(UserContext.builder()
                            .email(email)
                            .globalContext("Chưa có dữ liệu")
                            .build());
                });
    }

    private Mono<String> getSessionSummary(String sessionId) {
        return chatSessionRepository.findById(sessionId)
                .map(session -> session.getSummary() != null ? session.getSummary() : "Chưa có tóm tắt")
                .defaultIfEmpty("Chưa có tóm tắt")
                .onErrorResume(e -> {
                    log.error("Failed to fetch session summary for sessionId: {}", sessionId, e);
                    return Mono.just("Chưa có tóm tắt");
                });
    }

    private String buildFactsList(UserContext userContext) {
        if (userContext == null || userContext.getFacts() == null || userContext.getFacts().isEmpty()) {
            return "- Chưa có thông tin cá nhân";
        }

        return userContext.getFacts().stream()
                .filter(fact -> fact.getConfidence() != null && fact.getConfidence() >= 0.7)
                .map(fact -> String.format("- %s: %s (độ tin cậy: %.0f%%)",
                        fact.getKey(),
                        fact.getValue(),
                        fact.getConfidence() * 100))
                .collect(Collectors.joining("\n"));
    }

    private String parseAndFormatHobbies(String hobbiesJson) {
        if (hobbiesJson == null || hobbiesJson.isEmpty() || hobbiesJson.equals("[]") || hobbiesJson.equals("{}")) {
            return "- Chưa cập nhật";
        }
        
        try {
            try {
                List<String> hobbiesList = objectMapper.readValue(hobbiesJson, List.class);
                if (hobbiesList == null || hobbiesList.isEmpty()) {
                    return "- Chưa cập nhật";
                }
                return hobbiesList.stream()
                        .filter(hobby -> hobby != null && !hobby.toString().isEmpty())
                        .map(hobby -> "- " + hobby.toString())
                        .collect(Collectors.joining("\n"));
            } catch (com.fasterxml.jackson.databind.exc.MismatchedInputException e) {
                try {
                    Map<String, Object> hobbiesMap = objectMapper.readValue(hobbiesJson, Map.class);
                    if (hobbiesMap == null || hobbiesMap.isEmpty()) {
                        return "- Chưa cập nhật";
                    }
                    return hobbiesMap.values().stream()
                            .filter(hobby -> hobby != null && !hobby.toString().isEmpty())
                            .map(hobby -> "- " + hobby.toString())
                            .collect(Collectors.joining("\n"));
                } catch (Exception mapException) {
                    log.warn("Failed to parse hobbies as Map: {}", hobbiesJson, mapException);
                    return "- Chưa cập nhật";
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse hobbies JSON: {}", hobbiesJson, e);
            return "- Chưa cập nhật";
        }
    }

    private Mono<String> getRecentMessages(String sessionId, int limit) {
        return chatMessageRepository.findAllBySessionIdOrderByTimestampAsc(sessionId)
                .takeLast(limit)
                .map(msg -> msg.getSender().name().toLowerCase() + ": " + msg.getContent())
                .collectList()
                .map(messages -> {
                    if (messages.isEmpty()) {
                        return "Chưa có hội thoại trước đó";
                    }
                    return String.join("\n", messages);
                })
                .onErrorResume(e -> {
                    log.error("Failed to fetch recent messages for sessionId: {}", sessionId, e);
                    return Mono.just("Chưa có hội thoại trước đó");
                });
    }

    private static class TargetUserResolution {
        private final boolean hasTarget;
        private final String targetEmail;
        private final double confidence;

        public TargetUserResolution(boolean hasTarget, String targetEmail, double confidence) {
            this.hasTarget = hasTarget;
            this.targetEmail = targetEmail;
            this.confidence = confidence;
        }

        public boolean hasTarget() {
            return hasTarget && targetEmail != null;
        }

        public String getTargetEmail() {
            return targetEmail;
        }

        public double getConfidence() {
            return confidence;
        }
    }
}
