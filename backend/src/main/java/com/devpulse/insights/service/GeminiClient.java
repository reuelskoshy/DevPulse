package com.devpulse.insights.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.devpulse.common.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final String GENERATE_URL = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent";
    // Generous, because thinking models spend part of the output budget before they answer.
    private static final int MAX_OUTPUT_TOKENS = 4096;
    /** Rate limits and overloads ("model is experiencing high demand") that usually clear within seconds. */
    private static final Set<Integer> TRANSIENT_STATUSES = Set.of(429, 500, 502, 503, 504);
    /** Waits before each retry of the main model; the fallback model gets a single attempt. */
    private static final List<Duration> RETRY_BACKOFF = List.of(Duration.ofSeconds(1), Duration.ofSeconds(3));

    /** Pauses between attempts; replaced in tests so they don't sleep. */
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final Sleeper sleeper;

    @Autowired
    GeminiClient(RestClient.Builder restClientBuilder, GeminiProperties properties) {
        this(restClientBuilder, properties, duration -> Thread.sleep(duration));
    }

    GeminiClient(RestClient.Builder restClientBuilder, GeminiProperties properties, Sleeper sleeper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.sleeper = sleeper;
    }

    /**
     * Asks for JSON matching {@code responseSchema} (Gemini's OpenAPI-style schema) and returns the raw JSON text.
     * Temporary failures are retried with a short backoff, then tried once on the fallback model if one is set.
     */
    String generateJson(String prompt, Map<String, Object> responseSchema) {
        Map<String, Object> request = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "maxOutputTokens", MAX_OUTPUT_TOKENS,
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema));

        List<String> attempts = new ArrayList<>();
        for (int i = 0; i <= RETRY_BACKOFF.size(); i++) {
            attempts.add(properties.model());
        }
        String fallback = properties.fallbackModel();
        if (fallback != null && !fallback.isBlank() && !fallback.equals(properties.model())) {
            attempts.add(fallback);
        }

        for (int attempt = 0; attempt < attempts.size(); attempt++) {
            String model = attempts.get(attempt);
            try {
                return answerText(call(model, request));
            } catch (RestClientException exception) {
                boolean last = attempt == attempts.size() - 1;
                if (!isTransient(exception) || last) {
                    log.warn("Gemini request to {} failed after {} attempt(s).", model, attempt + 1, exception);
                    throw new ConflictException(isTransient(exception)
                            ? "The AI service is busy right now. Try again in a minute."
                            : "The AI service didn't respond. Try again in a minute.");
                }
                log.info("Gemini request to {} failed with a temporary error ({}); retrying.", model,
                        describe(exception));
                if (attempt < RETRY_BACKOFF.size()) {
                    pause(RETRY_BACKOFF.get(attempt));
                }
            }
        }
        throw new IllegalStateException("unreachable");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String model, Map<String, Object> request) {
        return restClient.post()
                .uri(GENERATE_URL, model)
                // In a header rather than the query string, so the key never appears in a logged URL.
                .header("x-goog-api-key", properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    private static String answerText(Map<String, Object> body) {
        List<Map<String, Object>> candidates = body == null ? null : (List<Map<String, Object>>) body.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new ConflictException("The AI service returned an empty answer. Try again in a minute.");
        }
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = content == null ? null : (List<Map<String, Object>>) content.get("parts");
        String text = parts == null ? "" : parts.stream()
                // Thinking models can return their reasoning as separate parts; only the answer matters here.
                .filter(part -> !Boolean.TRUE.equals(part.get("thought")))
                .map(part -> part.get("text"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.joining());
        if (text.isBlank()) {
            throw new ConflictException("The AI service returned an empty answer. Try again in a minute.");
        }
        return text;
    }

    private static boolean isTransient(RestClientException exception) {
        if (exception instanceof RestClientResponseException response) {
            return TRANSIENT_STATUSES.contains(response.getStatusCode().value());
        }
        // Timeouts and dropped connections.
        return exception instanceof ResourceAccessException;
    }

    private static String describe(RestClientException exception) {
        return exception instanceof RestClientResponseException response
                ? String.valueOf(response.getStatusCode().value())
                : exception.getClass().getSimpleName();
    }

    private void pause(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ConflictException("The AI service didn't respond. Try again in a minute.");
        }
    }
}
