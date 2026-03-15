package com.familier.ai.controller;

import org.junit.jupiter.api.Test;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for metadata extraction and removal in AiController
 */
class AiControllerMetadataTest {

    private static final Pattern METADATA_PATTERN = Pattern.compile("<suggestion_metadata>(.*?)</suggestion_metadata>",
            Pattern.DOTALL);

    @Test
    void testMetadataAtBeginning() {
        String content = "<suggestion_metadata>{\"title\":\"Task\"}</suggestion_metadata>Nội dung sau metadata";
        
        Matcher matcher = METADATA_PATTERN.matcher(content);
        assertTrue(matcher.find(), "Should find metadata");
        
        String metadata = matcher.group(1).trim();
        assertEquals("{\"title\":\"Task\"}", metadata);
        
        String cleaned = matcher.replaceAll("");
        assertEquals("Nội dung sau metadata", cleaned);
        assertFalse(cleaned.contains("<suggestion_metadata>"));
        assertFalse(cleaned.contains("</suggestion_metadata>"));
    }

    @Test
    void testMetadataInMiddle() {
        String content = "Nội dung trước <suggestion_metadata>{\"title\":\"Task\"}</suggestion_metadata> nội dung sau";
        
        Matcher matcher = METADATA_PATTERN.matcher(content);
        assertTrue(matcher.find(), "Should find metadata");
        
        String metadata = matcher.group(1).trim();
        assertEquals("{\"title\":\"Task\"}", metadata);
        
        String cleaned = matcher.replaceAll("");
        assertEquals("Nội dung trước  nội dung sau", cleaned);
        assertFalse(cleaned.contains("</suggestion_metadata>"));
    }

    @Test
    void testMetadataAtEnd() {
        String content = "Nội dung message <suggestion_metadata>{\"title\":\"Task\",\"description\":\"Do something\"}</suggestion_metadata>";
        
        Matcher matcher = METADATA_PATTERN.matcher(content);
        assertTrue(matcher.find(), "Should find metadata");
        
        String metadata = matcher.group(1).trim();
        assertEquals("{\"title\":\"Task\",\"description\":\"Do something\"}", metadata);
        
        String cleaned = matcher.replaceAll("");
        assertEquals("Nội dung message ", cleaned);
        assertFalse(cleaned.contains("<suggestion_metadata>"));
        assertFalse(cleaned.contains("</suggestion_metadata>"));
    }

    @Test
    void testMetadataWithMultipleLines() {
        String content = "Text before\n<suggestion_metadata>\n{\n  \"title\": \"Task\",\n  \"description\": \"Multi-line\"\n}\n</suggestion_metadata>\nText after";
        
        Matcher matcher = METADATA_PATTERN.matcher(content);
        assertTrue(matcher.find(), "Should find metadata");
        
        String cleaned = matcher.replaceAll("");
        assertTrue(cleaned.contains("Text before"));
        assertTrue(cleaned.contains("Text after"));
        assertFalse(cleaned.contains("<suggestion_metadata>"));
        assertFalse(cleaned.contains("</suggestion_metadata>"));
    }

    @Test
    void testNoMetadata() {
        String content = "Chỉ có nội dung thông thường không có metadata";
        
        Matcher matcher = METADATA_PATTERN.matcher(content);
        assertFalse(matcher.find(), "Should not find metadata");
        
        String cleaned = matcher.replaceAll("");
        assertEquals(content, cleaned);
    }

    @Test
    void testIncompleteMetadataStart() {
        String content = "Nội dung <suggestion_metadata>{\"title\":\"Task\"";
        
        Matcher matcher = METADATA_PATTERN.matcher(content);
        assertFalse(matcher.find(), "Should not match incomplete metadata");
    }

    @Test
    void testIncompleteMetadataEnd() {
        String content = "Nội dung {\"title\":\"Task\"}</suggestion_metadata>";
        
        Matcher matcher = METADATA_PATTERN.matcher(content);
        assertFalse(matcher.find(), "Should not match without opening tag");
    }

    @Test
    void testValidJson() {
        String validJson = "{\"title\":\"Task\",\"description\":\"Do something\"}";
        assertTrue(isValidJson(validJson), "Should be valid JSON");
    }

    @Test
    void testInvalidJsonTrailingComma() {
        String invalidJson = "{\"title\":\"Task\",\"description\":\"Do something\",}";
        assertFalse(isValidJson(invalidJson), "Should be invalid JSON (trailing comma)");
    }

    @Test
    void testInvalidJsonMissingQuotes() {
        String invalidJson = "{title:\"Task\",\"description\":\"Do something\"}";
        assertFalse(isValidJson(invalidJson), "Should be invalid JSON (missing quotes)");
    }

    @Test
    void testInvalidJsonMissingBrace() {
        String invalidJson = "{\"title\":\"Task\",\"description\":\"Do something\"";
        assertFalse(isValidJson(invalidJson), "Should be invalid JSON (missing closing brace)");
    }

    @Test
    void testEmptyJson() {
        String emptyJson = "";
        assertFalse(isValidJson(emptyJson), "Empty string should be invalid");
    }

    @Test
    void testNullJson() {
        assertFalse(isValidJson(null), "Null should be invalid");
    }

    @Test
    void testComplexValidJson() {
        String complexJson = "{\"title\":\"Buy flowers\",\"description\":\"Remember to buy red roses\",\"dueDate\":\"2024-05-20\",\"assigneeEmail\":\"partner@example.com\"}";
        assertTrue(isValidJson(complexJson), "Should be valid complex JSON");
    }

    @Test
    void testJsonWithSpecialCharacters() {
        String jsonWithSpecial = "{\"title\":\"Task with \\\"quotes\\\"\",\"description\":\"Line 1\\nLine 2\"}";
        assertTrue(isValidJson(jsonWithSpecial), "Should handle escaped characters");
    }

    @Test
    void testMetadataExtractionPositions() {
        String content = "Start <suggestion_metadata>{\"key\":\"value\"}</suggestion_metadata> End";
        
        Matcher matcher = METADATA_PATTERN.matcher(content);
        assertTrue(matcher.find());
        
        int startPos = matcher.start();
        int endPos = matcher.end();
        
        // Verify positions
        // "Start " = 6 chars (0-5)
        // "<suggestion_metadata>{\"key\":\"value\"}</suggestion_metadata>" = 58 chars (6-63)
        // " End" = 4 chars (64-67)
        assertEquals(6, startPos, "Metadata should start at position 6");
        assertEquals(64, endPos, "Metadata should end at position 64");
        
        String before = content.substring(0, startPos);
        String after = content.substring(endPos);
        
        assertEquals("Start ", before);
        assertEquals(" End", after);
    }

    // Helper method matching the one in AiController
    private boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }
        
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(json);
            return true;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return false;
        }
    }
}
