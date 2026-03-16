package com.familier.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class BuildVariablesResult {
    private Map<String, String> variables;
    private UnifiedDetectionResult detection;
    private String targetUserEmail;
}
