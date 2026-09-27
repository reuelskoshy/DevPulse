package com.devpulse.insights.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class GeminiClient {

    private static final String GENERATE_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}";
    private static final int MAX_OUTPUT_TOKENS = 512;

    private final RestClient restClient;
    private final GeminiProperties properties;

    GeminiClient(RestClient.Builder restClientBuilder, GeminiProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    String summarize(String prompt) {
        Map<String, Object> body = restClient.post()
                .uri(GENERATE_URL, properties.model(), properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                        "generationConfig", Map.of("maxOutputTokens", MAX_OUTPUT_TOKENS)))
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> candidates = body == null ? null : (List<Map<String, Object>>) body.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("Gemini API returned an empty response.");
        }
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = content == null ? null : (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            throw new IllegalStateException("Gemini API returned no text content.");
        }
        return (String) parts.get(0).get("text");
    }
}
