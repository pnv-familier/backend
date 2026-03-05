package com.familier.ai.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAllExceptions(Exception ex) {
        return Mono.just(
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Internal Server Error",
                    "message", ex.getMessage(),
                    "status", 500
                ))
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArgument(IllegalArgumentException ex) {
        return Mono.just(
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                    "error", "Bad Request",
                    "message", ex.getMessage(),
                    "status", 400
                ))
        );
    }
}
