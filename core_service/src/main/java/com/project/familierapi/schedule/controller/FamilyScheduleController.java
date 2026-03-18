package com.project.familierapi.schedule.controller;

import com.project.familierapi.schedule.dto.CalendarResponse;
import com.project.familierapi.schedule.dto.CreateEventRequest;
import com.project.familierapi.schedule.dto.EventResponse;
import com.project.familierapi.schedule.service.FamilyScheduleService;
import com.project.familierapi.shared.dto.SuccessResponse;
import com.project.familierapi.user.domain.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping({"api/v1/schedule", "schedule"})
@RequiredArgsConstructor
public class FamilyScheduleController {
    private final FamilyScheduleService scheduleService;

    @PostMapping("/events")
    public ResponseEntity<SuccessResponse<EventResponse>> createEvent(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CreateEventRequest request) {
        EventResponse response = scheduleService.createEvent(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SuccessResponse<>("Event created successfully", response));
    }

    @GetMapping("/calendar")
    public ResponseEntity<SuccessResponse<CalendarResponse>> getCalendar(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        CalendarResponse response = scheduleService.getCalendarEvents(user, startDate, endDate);
        return ResponseEntity.ok(new SuccessResponse<>(response.getMessage(), response));
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<SuccessResponse<EventResponse>> getEventDetail(
            @AuthenticationPrincipal User user,
            @PathVariable Integer eventId) {
        EventResponse response = scheduleService.getEventDetail(user, eventId);
        return ResponseEntity.ok(new SuccessResponse<>("Event details retrieved", response));
    }

    @PutMapping("/events/{eventId}")
    public ResponseEntity<SuccessResponse<EventResponse>> updateEvent(
            @AuthenticationPrincipal User user,
            @PathVariable Integer eventId,
            @Valid @RequestBody CreateEventRequest request) {
        EventResponse response = scheduleService.updateEvent(user, eventId, request);
        return ResponseEntity.ok(new SuccessResponse<>("Event updated successfully", response));
    }

    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<SuccessResponse<Void>> deleteEvent(
            @AuthenticationPrincipal User user,
            @PathVariable Integer eventId) {
        scheduleService.deleteEvent(user, eventId);
        return ResponseEntity.ok(new SuccessResponse<>("Event deleted successfully", null));
    }
}