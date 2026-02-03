package com.project.familierapi.family.domain;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "families")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Family {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false, length = 20, name = "invite_code")
    private String inviteCode;

    @Column(nullable = false, name = "user_id")
    private String userId;
}