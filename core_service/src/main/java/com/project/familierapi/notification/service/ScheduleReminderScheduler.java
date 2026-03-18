package com.project.familierapi.notification.service;

import com.project.familierapi.notification.domain.NotificationType;
import com.project.familierapi.schedule.domain.EventParticipant;
import com.project.familierapi.schedule.repository.FamilyEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ScheduleReminderScheduler {

    private final FamilyEventRepository eventRepository;
    private final NotificationService notificationService;

    // AC-NT-13: Run every minute, find events starting in 30 minutes
    @Scheduled(fixedDelay = 60000)
    @org.springframework.transaction.annotation.Transactional
    public void sendEventReminders() {
        LocalDateTime from = LocalDateTime.now().plusMinutes(29);
        LocalDateTime to = LocalDateTime.now().plusMinutes(31);

        eventRepository.findByStartTimeBetween(from, to).forEach(event -> {
            if (event.getParticipants() == null) return;
            event.getParticipants().stream()
                    .map(EventParticipant::getUser)
                    .forEach(user -> notificationService.createAndPush(
                            user, null, NotificationType.SCHEDULE,
                            "\u23f0 Event Reminder",
                            event.getTitle() + " starts in 30 minutes",
                            String.valueOf(event.getEventId())));
        });
    }
}
