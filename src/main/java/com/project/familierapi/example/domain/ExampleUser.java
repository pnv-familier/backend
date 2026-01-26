package com.project.familierapi.example.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExampleUser {
    @Id
    private String id = UUID.randomUUID().toString();
    private String name;
    private String email;
    private String password;
}
