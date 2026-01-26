package com.project.familierapi.example.controller;

import com.project.familierapi.example.dto.ExampleResponseDto;
import com.project.familierapi.example.dto.ExampleUserRequestDto;
import com.project.familierapi.example.service.ExampleUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/example")
public class ExampleUserController {

    private final ExampleUserService exampleUserService;

    @PostMapping("/users")
    public ResponseEntity<String> createUser(@RequestBody ExampleUserRequestDto exampleUserRequestDto) {
        return ResponseEntity.ok(exampleUserService.createUser(exampleUserRequestDto));
    }

    @GetMapping("/users")
    public ResponseEntity<List<ExampleResponseDto>> getUsers() {
        return ResponseEntity.ok(exampleUserService.getUsers());
    }
}
