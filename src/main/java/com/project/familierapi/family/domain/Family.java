package com.project.familierapi.family.domain;

import com.project.familierapi.user.domain.User;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;


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

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "family", cascade = CascadeType.ALL)
    private List<FamilyMember> members;
}
