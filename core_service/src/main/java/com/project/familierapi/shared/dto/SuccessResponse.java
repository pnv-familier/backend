package com.project.familierapi.shared.dto;

public record SuccessResponse<T> (
        String message,
        T data
) {
}
