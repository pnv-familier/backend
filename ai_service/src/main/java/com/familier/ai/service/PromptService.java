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

    public String loadSystemPrompt(String fileName, Map<String, String> variables, boolean includeSuggestionMetadata, String suggestionType) throws Exception {
        String content = loadRawContent(fileName);
        content = enrichPrompt(content, variables);
        
        if (includeSuggestionMetadata && suggestionType != null) {
            content = injectSuggestionMetadataInstruction(content, suggestionType);
        }
        
        return content;
    }

    private String injectSuggestionMetadataInstruction(String content, String suggestionType) {
        String instruction = "\n\n# SUGGESTION METADATA INSTRUCTION\n" +
                "Bạn đã phát hiện một hành động tiềm năng. Hãy hỏi người dùng xác nhận trong văn bản (ví dụ: \"Bạn có muốn mình...?\").\n" +
                "Ở cuối phần hồi của bạn, thêm thẻ <suggestion_metadata> chứa JSON object theo cấu trúc sau:\n\n";
        
        switch (suggestionType) {
            case "EVENT":
                instruction += "{ \"title\": string, \"startTime\": \"HH:mm\", \"endTime\": \"HH:mm\", \"date\": int, \"month\": int, \"year\": int, \"location\": string|null }";
                break;
            case "TASK":
                instruction += "{ \"assigneeEmail\": string (email của người được giao), \"title\": string, \"description\": string }";
                break;
            case "OFFLINE":
                instruction += "{ \"action\": string (đề xuất hành động dựa trên sở thích của thành viên) }";
                break;
        }
        
        instruction += "\n\nLưu ý: KHÔNG bao gồm thẻ <suggestions> cũ nữa. Chỉ dùng <suggestion_metadata>.";
        
        return content + instruction;
    }

    private String enrichPrompt(String content, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) return content;

        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            if (content.contains(placeholder)) {
                content = content.replace(placeholder, entry.getValue());
            }
        }
        System.out.println("This is a prompt after chat" + content);
        return content;
    }

    private String loadRawContent(String fileName) throws Exception {
        Resource resource = resourceLoader.getResource("classpath:prompts/" + fileName + ".txt");

        try (java.io.InputStream is = resource.getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        }
    }

}
