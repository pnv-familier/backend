

package com.project.familierapi.example.service;

import com.project.familierapi.example.domain.ExampleUser;
import com.project.familierapi.example.dto.ExampleResponseDto;
import com.project.familierapi.example.dto.ExampleUserRequestDto;
import com.project.familierapi.example.exception.UserAlreadyExistsException;
import com.project.familierapi.example.repository.ExampleUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExampleUserService {

    private final ExampleUserRepository exampleUserRepository;

    @Transactional
    public String createUser(ExampleUserRequestDto exampleUserRequestDto) {
        exampleUserRepository.findByEmail(exampleUserRequestDto.email()).ifPresent(it -> {
            throw new UserAlreadyExistsException("user already exists");
        });
        exampleUserRepository.save(new ExampleUser(null, exampleUserRequestDto.name(), exampleUserRequestDto.email(), exampleUserRequestDto.password()));
        return "user created";
    }

    public List<ExampleResponseDto> getUsers() {
        return exampleUserRepository.findAll().stream()
                .map(it -> new ExampleResponseDto(it.getId(), it.getName(), it.getEmail()))
                .collect(Collectors.toList());
    }
}
