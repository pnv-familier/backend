package com.familier.ai.service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

/**
 * Simplified PromptService.
 * The suggestion-metadata injection and detection-specific parameters have been
 * removed — structured suggestion data is now handled via @Tool function calls,
 * not embedded XML in the prompt.
 */
@Service
public class PromptService {

    private final ResourceLoader resourceLoader;

    public PromptService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Loads a prompt template by name from classpath:prompts/{fileName}.txt and
     * substitutes all {{placeholder}} variables with values from the given map.
     */
    public String loadSystemPrompt(String fileName, Map<String, String> variables) throws Exception {
        String content = loadRawContent(fileName);
        return enrichPrompt(content, variables);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String enrichPrompt(String content, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) return content;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            if (content.contains(placeholder)) {
                content = content.replace(placeholder, entry.getValue());
            }
        }
        return content;
    }

    private String loadRawContent(String fileName) throws Exception {
        Resource resource = resourceLoader.getResource("classpath:prompts/" + fileName + ".txt");
        try (java.io.InputStream is = resource.getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        }
    }
}
