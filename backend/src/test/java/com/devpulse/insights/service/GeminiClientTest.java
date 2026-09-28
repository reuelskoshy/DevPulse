package com.devpulse.insights.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.devpulse.common.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiClientTest {

    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String MAIN = BASE + "main-model:generateContent";
    private static final String FALLBACK = BASE + "lite-model:generateContent";
    private static final String ANSWER = """
            {"candidates": [{"content": {"parts": [
              {"text": "thinking...", "thought": true},
              {"text": "{\\"headline\\": \\"Hi\\"}"}]}}]}
            """;

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final List<Duration> pauses = new ArrayList<>();

    private GeminiClient client(String fallbackModel) {
        return new GeminiClient(builder, new GeminiProperties("secret-key", "main-model", fallbackModel), pauses::add);
    }

    @Test
    void retriesAnOverloadedModelThenReturnsOnlyTheAnswerParts() {
        server.expect(requestTo(MAIN)).andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "secret-key"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(MAIN)).andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

        String text = client("lite-model").generateJson("prompt", Map.of("type", "OBJECT"));

        assertThat(text).isEqualTo("{\"headline\": \"Hi\"}");
        assertThat(pauses).containsExactly(Duration.ofSeconds(1));
        server.verify();
    }

    @Test
    void fallsBackToTheLighterModelWhenTheMainOneStaysBusy() {
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(MAIN)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }
        server.expect(requestTo(FALLBACK)).andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

        assertThat(client("lite-model").generateJson("prompt", Map.of())).contains("headline");
        assertThat(pauses).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(3));
        server.verify();
    }

    @Test
    void givesUpWithABusyMessageWhenEveryAttemptIsRateLimited() {
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(MAIN)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        }

        assertThatThrownBy(() -> client("").generateJson("prompt", Map.of()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("busy");
        server.verify();
    }

    @Test
    void aPermanentErrorIsNotRetried() {
        server.expect(requestTo(MAIN)).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client("lite-model").generateJson("prompt", Map.of()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("didn't respond");
        assertThat(pauses).isEmpty();
        server.verify();
    }
}
