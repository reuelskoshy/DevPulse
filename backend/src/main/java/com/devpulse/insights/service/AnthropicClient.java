package com.devpulse.insights.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class AnthropicClient {

    private static final String MESSAGES_URL = "https://api.anthropic.com/v1/messages";
    private static final int MAX_TOKENS = 300;

    private final RestClient restClient;
    private final AnthropicProperties properties;

    AnthropicClient(RestClient.Builder restClientBuilder, AnthropicProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    String summarize(String prompt) {
        Map<String, Object> body = restClient.post()
                .uri(MESSAGES_URL)
                .header("x-api-key", properties.apiKey())
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", properties.model(),
                        "max_tokens", MAX_TOKENS,
                        "messages", List.of(Map.of("role", "user", "content", prompt))))
                .retrieve()
                .body(Map.class);

        List<Map<String, Object>> content = body == null ? null : (List<Map<String, Object>>) body.get("content");
        if (content == null || content.isEmpty()) {
            throw new IllegalStateException("Anthropic API returned an empty response.");
        }
        return (String) content.get(0).get("text");
    }
}
