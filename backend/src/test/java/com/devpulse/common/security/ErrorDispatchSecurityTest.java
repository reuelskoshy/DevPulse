package com.devpulse.common.security;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.devpulse.common.exception.ApiExceptionHandler;
import com.devpulse.common.ratelimit.RateLimiter;
import com.devpulse.integration.github.GitHubIntegrationController;
import com.devpulse.integration.github.GitHubOAuthService;
import com.devpulse.sync.service.GitHubSyncService;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a real embedded Tomcat because MockMvc never performs the servlet ERROR dispatch to /error,
 * which is exactly where the bug lived. Only the web and security slice is loaded, so no database is needed.
 */
@SpringBootTest(
        classes = ErrorDispatchSecurityTest.SecurityWebSlice.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "devpulse.security.jwt.secret=ZGV2cHVsc2UtZXJyb3ItZGlzcGF0Y2gtdGVzdC1zaWduaW5nLWtleS0wMTIzNDU2Nzg5",
                "devpulse.security.jwt.access-token-expiration=PT1H",
                "devpulse.integrations.github.frontend-success-url=http://localhost:5173/app/dashboard"
        })
class ErrorDispatchSecurityTest {

    private static final String ENTRY_POINT_MESSAGE = "Authentication is required.";

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private GitHubOAuthService gitHubOAuthService;
    @MockitoBean private GitHubSyncService gitHubSyncService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void signedInRequestToUnknownPathReturns404InsteadOf401() throws Exception {
        HttpResponse<String> response = get("/api/v1/does-not-exist", validToken());

        assertThat(response.statusCode()).isEqualTo(404);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("status").asInt()).isEqualTo(404);
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/does-not-exist");
        assertThat(body.has("message")).isFalse();
    }

    @Test
    void signedInRequestMissingRequiredParameterReturns400() throws Exception {
        HttpResponse<String> response = get("/api/v1/test/needs-param", validToken());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.body()).get("status").asInt()).isEqualTo(400);
    }

    @Test
    void signedInRequestWithUnhandledExceptionReturns500WithoutLeakingDetails() throws Exception {
        HttpResponse<String> response = get("/api/v1/test/explode", validToken());

        assertThat(response.statusCode()).isEqualTo(500);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.get("status").asInt()).isEqualTo(500);
        assertThat(body.has("message")).isFalse();
        assertThat(body.has("trace")).isFalse();
        assertThat(response.body()).doesNotContain("internal detail");
    }

    @Test
    void anonymousRequestToProtectedEndpointStillGets401EntryPoint() throws Exception {
        HttpResponse<String> response = get("/api/v1/test/explode", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(objectMapper.readTree(response.body()).get("message").asText()).isEqualTo(ENTRY_POINT_MESSAGE);
    }

    @Test
    void anonymousRequestToUnknownPathStillGets401SoPathsCannotBeProbed() throws Exception {
        HttpResponse<String> response = get("/api/v1/does-not-exist", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(objectMapper.readTree(response.body()).get("message").asText()).isEqualTo(ENTRY_POINT_MESSAGE);
    }

    @Test
    void invalidTokenStillGets401() throws Exception {
        HttpResponse<String> response = get("/api/v1/does-not-exist", "not-a-real-jwt");

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void gitHubCallbackWithoutParametersRedirectsBackToAppInsteadOfShowingJson() throws Exception {
        HttpResponse<String> response = get("/api/v1/integrations/github/callback", null);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location"))
                .hasValue("http://localhost:5173/app/dashboard?github_error=failed");
    }

    @Test
    void gitHubCallbackAfterUserCancelsRedirectsWithDenied() throws Exception {
        HttpResponse<String> response = get("/api/v1/integrations/github/callback?error=access_denied&state=abc", null);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location"))
                .hasValue("http://localhost:5173/app/dashboard?github_error=denied");
    }

    private String validToken() {
        return jwtService.createAccessToken(new DpUser("Dev User", "dev@example.com", "unused", DpUserRole.MEMBER));
    }

    private HttpResponse<String> get(String path, String bearerToken) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class, ApiExceptionHandler.class,
            GitHubIntegrationController.class, RateLimiter.class, TestEndpoints.class})
    static class SecurityWebSlice {
    }

    @RestController
    static class TestEndpoints {

        @GetMapping("/api/v1/test/explode")
        String explode() {
            throw new IllegalStateException("internal detail");
        }

        @GetMapping("/api/v1/test/needs-param")
        String needsParam(@RequestParam String value) {
            return value;
        }
    }
}
