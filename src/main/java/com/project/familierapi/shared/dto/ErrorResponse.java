package com.project.familierapi.shared.dto;

import java.util.Map;

public class ErrorResponse {
    public final String message;
    public final String path;
    public Map<String, String> details;

    public ErrorResponse(String message, String path) {
        this.message = message;
        this.path = path;
    }

    public ErrorResponse(String message, String path, Map<String, String> details) {
        this.message = message;
        this.path = path;
        this.details = details;
    }
}
