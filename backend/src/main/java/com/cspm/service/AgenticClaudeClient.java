package com.cspm.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@Slf4j
public class AgenticClaudeClient {

    @Value("${anthropic.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";

    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Send a message to Claude with tools. Returns the raw response body map.
     * The response may contain tool_use blocks or text blocks.
     *
     * @param messages    List of message maps (role + content)
     * @param tools       List of tool definition maps
     * @param systemPrompt System prompt string
     * @return Response body as Map
     */
    public Map<String, Object> sendWithTools(List<Map<String, Object>> messages,
                                              List<Map<String, Object>> tools,
                                              String systemPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "claude-sonnet-4-20250514");
        body.put("max_tokens", 4096);
        body.put("system", systemPrompt);
        body.put("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                CLAUDE_API_URL, HttpMethod.POST, entity, Map.class);

        return response.getBody() != null ? response.getBody() : Map.of();
    }

    /**
     * Extract content blocks from Claude response.
     * Returns list of content block maps, each with "type" key.
     * Types: "text" (has "text"), "tool_use" (has "id", "name", "input")
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> extractContentBlocks(Map<String, Object> response) {
        Object content = response.get("content");
        if (content instanceof List) {
            return (List<Map<String, Object>>) content;
        }
        return List.of();
    }

    /**
     * Check if response has stop_reason "tool_use" meaning Claude wants to call tools.
     */
    public boolean hasToolUse(Map<String, Object> response) {
        return "tool_use".equals(response.get("stop_reason"));
    }

    /**
     * Extract the final text from a response (concatenate all text blocks).
     */
    public String extractText(Map<String, Object> response) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> block : extractContentBlocks(response)) {
            if ("text".equals(block.get("type"))) {
                sb.append(block.get("text"));
            }
        }
        return sb.toString();
    }
}
