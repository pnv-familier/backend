package com.project.familierapi.notification.service;

import com.project.familierapi.notification.repository.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final PushTokenRepository pushTokenRepository;
    private final RestClient restClient = RestClient.create();

    @Value("${expo.push-url}")
    private String expoPushUrl;

    public void sendToUser(String userId, String title, String body, String type, String referenceId) {
        List<String> tokens = pushTokenRepository.findByUserId(userId)
                .stream().map(t -> t.getToken()).collect(Collectors.toList());
        if (tokens.isEmpty()) return;
        sendBatch(tokens, title, body, type, referenceId);
    }

    public void sendToUsers(List<String> userIds, String title, String body, String type, String referenceId) {
        List<String> tokens = userIds.stream()
                .flatMap(uid -> pushTokenRepository.findByUserId(uid).stream())
                .map(t -> t.getToken())
                .collect(Collectors.toList());
        if (tokens.isEmpty()) return;
        sendBatch(tokens, title, body, type, referenceId);
    }

    private void sendBatch(List<String> tokens, String title, String body, String type, String referenceId) {
        List<Map<String, Object>> messages = tokens.stream().map(token -> Map.<String, Object>of(
                "to", token,
                "title", title,
                "body", body,
                "data", Map.of("type", type, "referenceId", referenceId != null ? referenceId : ""),
                "sound", "default"
        )).collect(Collectors.toList());

        try {
            Map<?, ?> response = restClient.post()
                    .uri(expoPushUrl)
                    .body(messages)
                    .retrieve()
                    .body(Map.class);
            handleExpoPushResponse(response, tokens);
        } catch (Exception e) {
            log.error("Failed to send push notification: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleExpoPushResponse(Map<?, ?> response, List<String> tokens) {
        if (response == null || !response.containsKey("data")) return;
        List<?> data = (List<?>) response.get("data");
        for (int i = 0; i < data.size() && i < tokens.size(); i++) {
            Map<?, ?> result = (Map<?, ?>) data.get(i);
            if ("error".equals(result.get("status")) && "DeviceNotRegistered".equals(result.get("details"))) {
                pushTokenRepository.deleteByToken(tokens.get(i));
                log.info("Removed expired push token: {}", tokens.get(i));
            }
        }
    }
}
