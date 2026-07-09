package com.project.familierapi.notification.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.familierapi.auth.repository.TokenRepository;
import com.project.familierapi.notification.dto.UrgentSuggestionResponse;
import com.project.familierapi.shared.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrgentSuggestionWebSocketHandler extends TextWebSocketHandler {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final TokenRepository tokenRepository;
    private final ObjectMapper objectMapper;

    // userId -> Map<sessionId, session> — hỗ trợ 1 user nhiều thiết bị
    private final Map<String, Map<String, WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractToken(session);
        if (token == null) {
            closeWithError(session, 4001, "Missing token");
            return;
        }

        try {
            String email = jwtService.extractUsername(token);
            if (email == null) {
                closeWithError(session, 4001, "Invalid token");
                return;
            }

            var userDetails = userDetailsService.loadUserByUsername(email);
            boolean isTokenValid = tokenRepository.findByToken(token)
                    .map(t -> !t.isExpired() && !t.isRevoked())
                    .orElse(false);

            if (!jwtService.isTokenValid(token, userDetails) || !isTokenValid) {
                closeWithError(session, 4001, "Invalid token");
                return;
            }

            String userId = ((com.project.familierapi.user.domain.User) userDetails).getId();
            session.getAttributes().put("userId", userId);

            userSessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                    .put(session.getId(), session);

            log.info("[WS] User {} connected sessionId={} totalConnections={}",
                    userId, session.getId(), countTotalConnections());

        } catch (Exception e) {
            log.error("[WS] Auth error: {}", e.getMessage());
            closeWithError(session, 4001, "Invalid token");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            Map<String, WebSocketSession> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session.getId());
                if (sessions.isEmpty()) userSessions.remove(userId);
            }
            log.info("[WS] User {} disconnected sessionId={} reason={}",
                    userId, session.getId(), status.getReason());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Client gửi "ping" → server pong để giữ connection
        if ("ping".equals(message.getPayload())) {
            try {
                session.sendMessage(new TextMessage("pong"));
            } catch (IOException e) {
                log.warn("[WS] Failed to send pong sessionId={}", session.getId());
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[WS] Transport error sessionId={}: {}", session.getId(), exception.getMessage());
        afterConnectionClosed(session, CloseStatus.SERVER_ERROR);
    }

    /**
     * Gửi urgent suggestion real-time đến tất cả sessions của 1 user.
     * Được gọi từ UrgentSuggestionService sau khi saveAll() commit.
     */
    public void sendToUser(String userId, UrgentSuggestionResponse suggestion) {
        Map<String, WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("[WS] No active sessions for userId={}, will rely on REST polling", userId);
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(suggestion);
            TextMessage message = new TextMessage(payload);
            sessions.values().forEach(session -> {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                        log.info("[WS] Sent suggestion={} to userId={} sessionId={}",
                                suggestion.getId(), userId, session.getId());
                    } catch (IOException e) {
                        log.error("[WS] Failed to send to userId={} sessionId={}: {}",
                                userId, session.getId(), e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            log.error("[WS] Serialize error for userId={}: {}", userId, e.getMessage());
        }
    }

    public boolean isUserConnected(String userId) {
        Map<String, WebSocketSession> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    private String extractToken(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) return null;
        for (String param : uri.getQuery().split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "token".equals(kv[0])) return kv[1];
        }
        return null;
    }

    private void closeWithError(WebSocketSession session, int code, String reason) {
        try {
            session.close(new CloseStatus(code, reason));
            log.warn("[WS] Rejected sessionId={} reason={}", session.getId(), reason);
        } catch (IOException e) {
            log.error("[WS] Failed to close session: {}", e.getMessage());
        }
    }

    private int countTotalConnections() {
        return userSessions.values().stream().mapToInt(Map::size).sum();
    }
}
