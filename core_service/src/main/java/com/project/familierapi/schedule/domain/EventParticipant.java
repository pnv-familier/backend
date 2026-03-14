package com.project.familierapi.schedule.domain;

import com.project.familierapi.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "event_participants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private FamilyEvent event;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
