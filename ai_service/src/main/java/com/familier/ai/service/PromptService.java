package com.familier.ai.service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
public class PromptService {
    private final ResourceLoader resourceLoader;

    public PromptService(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public String loadSystemPrompt(String fileName, Map<String, String> variables) throws Exception {
        String content = loadRawContent(fileName);

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
        Resource resource = resourceLoader.getResource("classpath:prompts/" + fileName + ".md");

        String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        return content;
    }

}
