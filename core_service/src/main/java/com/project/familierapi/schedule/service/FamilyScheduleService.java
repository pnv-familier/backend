package com.project.familierapi.schedule.service;

import com.project.familierapi.notification.domain.NotificationType;
import com.project.familierapi.notification.service.NotificationService;
import com.project.familierapi.auth.repository.UserRepository;
import com.project.familierapi.family.domain.Family;
import com.project.familierapi.family.domain.FamilyMember;
import com.project.familierapi.schedule.domain.EventParticipant;
import com.project.familierapi.schedule.domain.FamilyEvent;
import com.project.familierapi.schedule.dto.CalendarResponse;
import com.project.familierapi.schedule.dto.CreateEventRequest;
import com.project.familierapi.schedule.dto.EventResponse;
import com.project.familierapi.schedule.exception.EventNotFoundException;
import com.project.familierapi.family.repository.FamilyMemberRepository;
import com.project.familierapi.schedule.repository.FamilyEventRepository;
import com.project.familierapi.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional
public class FamilyScheduleService {
    private final FamilyEventRepository eventRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final FamilyMemberRepository familyMemberRepository;

    @Transactional
    public EventResponse createEvent(User user, CreateEventRequest request) {
        FamilyMember familyMember = user.getFamily();
        if (familyMember == null) {
            throw new IllegalStateException("User is not part of any family");
        }

        Family family = familyMember.getFamily();
        
        FamilyEvent event = FamilyEvent.builder()
                .family(family)
                .creator(user)
                .title(request.getTitle())
                .description(request.getDescription())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .location(request.getLocation())
                .build();

        FamilyEvent savedEvent = eventRepository.save(event);

        if (request.getParticipantIds() != null && !request.getParticipantIds().isEmpty()) {
            List<EventParticipant> participants = request.getParticipantIds().stream()
                    .map(userId -> userRepository.findById(userId).orElse(null))
                    .filter(u -> u != null)
                    .map(u -> EventParticipant.builder().event(savedEvent).user(u).build())
                    .collect(Collectors.toList());
            savedEvent.setParticipants(participants);
            eventRepository.save(savedEvent);
        }

        // Notify all family members
        familyMemberRepository.findByFamilyIdOrderByJoinedAt(family.getId()).stream()
                .map(FamilyMember::getUser)
                .forEach(member -> notificationService.createAndPush(
                        member, null, NotificationType.SCHEDULE,
                        "📅 New Family Event",
                        user.getFullName() + " created: " + request.getTitle(),
                        String.valueOf(savedEvent.getEventId())));

        return mapToEventResponse(savedEvent);
    }

    @Transactional(readOnly = true)
    public CalendarResponse getCalendarEvents(User user, LocalDateTime startDate, LocalDateTime endDate) {
        FamilyMember familyMember = user.getFamily();
        if (familyMember == null) {
            throw new IllegalStateException("User is not part of any family");
        }

        String familyId = familyMember.getFamily().getId();
        List<FamilyEvent> events;

        if (startDate != null && endDate != null) {
            events = eventRepository.findByFamilyIdAndStartTimeBetweenOrderByStartTimeAsc(
                    familyId, startDate, endDate);
        } else {
            events = eventRepository.findByFamilyIdOrderByStartTimeAsc(familyId);
        }

        List<EventResponse> eventResponses = events.stream()
                .map(this::mapToEventResponse)
                .collect(Collectors.toList());

        String message = events.isEmpty() ? "No upcoming events" : "Events retrieved successfully";

        return CalendarResponse.builder()
                .events(eventResponses)
                .totalEvents(eventResponses.size())
                .message(message)
                .build();
    }

    @Transactional(readOnly = true)
    public EventResponse getEventDetail(User user, Integer eventId) {
        FamilyMember familyMember = user.getFamily();
        if (familyMember == null) {
            throw new IllegalStateException("User is not part of any family");
        }

        FamilyEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with id: " + eventId));

        if (!event.getFamily().getId().equals(familyMember.getFamily().getId())) {
            throw new IllegalStateException("Event does not belong to user's family");
        }

        return mapToEventResponse(event);
    }

    @Transactional
    public EventResponse updateEvent(User user, Integer eventId, CreateEventRequest request) {
        FamilyEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with id: " + eventId));

        FamilyMember familyMember = user.getFamily();
        if (!event.getFamily().getId().equals(familyMember.getFamily().getId())) {
            throw new IllegalStateException("Event does not belong to user's family");
        }

        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setLocation(request.getLocation());

        if (request.getParticipantIds() != null) {
            event.getParticipants().clear();
            List<EventParticipant> participants = request.getParticipantIds().stream()
                    .map(userId -> userRepository.findById(userId).orElse(null))
                    .filter(u -> u != null)
                    .map(u -> EventParticipant.builder().event(event).user(u).build())
                    .collect(Collectors.toList());
            event.getParticipants().addAll(participants);
        }

        FamilyEvent updatedEvent = eventRepository.save(event);
        return mapToEventResponse(updatedEvent);
    }

    @Transactional
    public void deleteEvent(User user, Integer eventId) {
        FamilyEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with id: " + eventId));

        FamilyMember familyMember = user.getFamily();
        if (!event.getFamily().getId().equals(familyMember.getFamily().getId())) {
            throw new IllegalStateException("Event does not belong to user's family");
        }

        eventRepository.delete(event);
    }

    private EventResponse mapToEventResponse(FamilyEvent event) {
        return EventResponse.builder()
                .eventId(event.getEventId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .location(event.getLocation())
                .creator(EventResponse.CreatorInfo.builder()
                        .userId(event.getCreator().getId())
                        .fullName(event.getCreator().getFullName())
                        .avatarUrl(event.getCreator().getAvatarUrl())
                        .build())
                .createdAt(event.getCreatedAt())
                .participantIds(event.getParticipants() != null
                        ? event.getParticipants().stream().map(p -> p.getUser().getId()).collect(Collectors.toList())
                        : List.of())
                .build();
    }
}