package com.project.familierapi.notification.service;

import com.project.familierapi.auth.repository.UserRepository;
import com.project.familierapi.family.domain.FamilyMember;
import com.project.familierapi.family.repository.FamilyMemberRepository;
import com.project.familierapi.notification.domain.NotificationType;
import com.project.familierapi.notification.domain.UrgentSuggestion;
import com.project.familierapi.notification.dto.BroadcastRequest;
import com.project.familierapi.notification.dto.UrgentSuggestionResponse;
import com.project.familierapi.notification.repository.UrgentSuggestionRepository;
import com.project.familierapi.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import com.project.familierapi.notification.websocket.UrgentSuggestionWebSocketHandler;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrgentSuggestionService {

    private final UrgentSuggestionRepository urgentSuggestionRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final UrgentSuggestionWebSocketHandler webSocketHandler;

    @Transactional
    public void broadcast(BroadcastRequest request) {
        // Lấy thành viên gia đình của sender
        FamilyMember senderMember = familyMemberRepository
                .findByUserEmail(request.getSenderEmail())
                .orElse(null);

        if (senderMember == null) {
            log.warn("[UrgentSuggestion] Sender not in any family: {}", request.getSenderEmail());
            return;
        }

        // Resolve senderName từ DB thay vì dùng email
        User senderUser = userRepository.findByEmail(request.getSenderEmail()).orElse(null);
        String resolvedSenderName = (senderUser != null && senderUser.getFullName() != null)
                ? senderUser.getFullName()
                : request.getSenderName();

        String familyId = senderMember.getFamily().getId();
        List<FamilyMember> members = familyMemberRepository.findByFamilyIdOrderByJoinedAt(familyId);

        String renderedMessage = renderTemplate(resolvedSenderName, request.getEmotion(), request.getContext());

        List<UrgentSuggestion> suggestions = members.stream()
                .filter(m -> !m.getUser().getEmail().equals(request.getSenderEmail()))
                .map(m -> UrgentSuggestion.builder()
                        .familyId(familyId)
                        .recipient(m.getUser())
                        .senderEmail(request.getSenderEmail())
                        .senderName(resolvedSenderName)
                        .emotion(request.getEmotion())
                        .context(request.getContext())
                        .subType(request.getSubType())
                        .build())
                .collect(Collectors.toList());

        if (suggestions.isEmpty()) {
            log.info("[UrgentSuggestion] No recipients for sender={}", request.getSenderEmail());
            return;
        }

        urgentSuggestionRepository.saveAll(suggestions);
        log.info("[UrgentSuggestion] Created {} suggestions for family={}", suggestions.size(), familyId);

        // Gửi real-time qua WebSocket + push notification
        suggestions.forEach(s -> {
            UrgentSuggestionResponse response = toResponse(s);
            // WebSocket — nếu user đang online nhận ngay
            webSocketHandler.sendToUser(s.getRecipient().getId(), response);
            // Push notification — fallback khi user offline hoặc app background
            notificationService.createAndPush(
                    s.getRecipient(),
                    senderUser,
                    NotificationType.AI,
                    "🤖 Familier Assistant",
                    renderedMessage,
                    s.getId());
        });
    }

    @Transactional(readOnly = true)
    public List<UrgentSuggestionResponse> getUnread(String recipientId) {
        return urgentSuggestionRepository
                .findByRecipientIdAndReadFalseOrderByCreatedAtDesc(recipientId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markAsRead(String id, String recipientId) {
        urgentSuggestionRepository.findById(id).ifPresent(s -> {
            if (s.getRecipient().getId().equals(recipientId)) {
                s.setRead(true);
                urgentSuggestionRepository.save(s);
            }
        });
    }

    @Transactional
    public void markAllAsRead(String recipientId) {
        List<UrgentSuggestion> unread = urgentSuggestionRepository
                .findByRecipientIdAndReadFalseOrderByCreatedAtDesc(recipientId);
        unread.forEach(s -> s.setRead(true));
        urgentSuggestionRepository.saveAll(unread);
    }

    private String renderTemplate(String senderName, String emotion, String context) {
        if (context != null && !context.isBlank()) {
            return String.format(
                    "🤖 Familier Assistant: %s đang %s vì %s. Có vẻ đây là lúc tốt để bạn gửi một lời hỏi thăm hoặc một sticker động viên đấy.",
                    senderName, emotion, context);
        }
        return String.format(
                "🤖 Familier Assistant: %s đang %s. Có vẻ đây là lúc tốt để bạn gửi một lời hỏi thăm hoặc một sticker động viên đấy.",
                senderName, emotion);
    }

    private UrgentSuggestionResponse toResponse(UrgentSuggestion s) {
        return UrgentSuggestionResponse.builder()
                .id(s.getId())
                .senderName(s.getSenderName())
                .emotion(s.getEmotion())
                .context(s.getContext())
                .subType(s.getSubType())
                .message(renderTemplate(s.getSenderName(), s.getEmotion(), s.getContext()))
                .read(s.isRead())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
