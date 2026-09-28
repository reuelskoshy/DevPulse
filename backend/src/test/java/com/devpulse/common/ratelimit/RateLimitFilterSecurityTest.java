package com.devpulse.common.ratelimit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import com.devpulse.auth.api.AuthController;
import com.devpulse.auth.api.AuthResponse;
import com.devpulse.auth.api.UserResponse;
import com.devpulse.auth.service.AuthenticationService;
import com.devpulse.common.exception.ApiExceptionHandler;
import com.devpulse.common.security.JwtAuthenticationFilter;
import com.devpulse.common.security.JwtService;
import com.devpulse.common.security.SecurityConfig;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Runs against a real embedded Tomcat with the real SecurityConfig, exactly where the filter is installed in
 * production. Each test uses its own X-Forwarded-For address so the shared, class-scoped Spring context (and so
 * the shared RateLimiter buckets) can't leak state between tests.
 */
@SpringBootTest(
        classes = RateLimitFilterSecurityTest.SecurityWebSlice.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "devpulse.security.jwt.secret=ZGV2cHVsc2UtcmF0ZS1saW1pdC10ZXN0LXNpZ25pbmcta2V5LTAxMjM0NTY3ODk=",
                "devpulse.security.jwt.access-token-expiration=PT1H"
        })
class RateLimitFilterSecurityTest {

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApplicationContext applicationContext;

    @MockitoBean private AuthenticationService authenticationService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void anIpKeyedRuleBlocksAfterItsCapacityAndSendsRetryAfter() throws Exception {
        when(authenticationService.register(any())).thenReturn(new AuthResponse("new-token",
                new UserResponse(UUID.randomUUID(), "new.person@example.com", "MEMBER", null, false)));
        String ip = "203.0.113.10";

        for (int i = 0; i < RateLimitRule.REGISTER.capacity(); i++) {
            assertThat(register(ip).statusCode()).isEqualTo(201);
        }
        HttpResponse<String> blocked = register(ip);

        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Retry-After")).isPresent();
        assertThat(objectMapper.readTree(blocked.body()).get("message").asText())
                .isEqualTo(RateLimitRule.REGISTER.message());
    }

    @Test
    void aDifferentIpGetsItsOwnBucketForTheSameRule() throws Exception {
        when(authenticationService.register(any())).thenReturn(new AuthResponse("new-token",
                new UserResponse(UUID.randomUUID(), "another.person@example.com", "MEMBER", null, false)));
        String exhausted = "203.0.113.20";
        for (int i = 0; i < RateLimitRule.REGISTER.capacity(); i++) {
            register(exhausted);
        }
        assertThat(register(exhausted).statusCode()).isEqualTo(429);

        assertThat(register("203.0.113.21").statusCode()).isEqualTo(201);
    }

    @Test
    void aUserKeyedRuleBlocksThatUserWithoutAffectingAnother() throws Exception {
        // Each call to regularToken() mints a DpUser with a fresh random id, so the token must be reused: the
        // bucket is keyed by user id, not email.
        String memberOne = regularToken("member.one@example.com");
        for (int i = 0; i < RateLimitRule.DIGEST_TEST.capacity(); i++) {
            assertThat(sendTestDigest(memberOne).statusCode()).isEqualTo(200);
        }

        assertThat(sendTestDigest(memberOne).statusCode()).isEqualTo(429);
        assertThat(sendTestDigest(regularToken("member.two@example.com")).statusCode()).isEqualTo(200);
    }

    /**
     * A Filter bean is also registered in the servlet container's own chain, where the security context is still
     * empty, and whichever copy of a OncePerRequestFilter runs first disables the other.
     */
    @Test
    void theGuardIsNotASpringBean() {
        assertThat(AnnotatedElementUtils.hasAnnotation(RateLimitFilter.class, Component.class)).isFalse();
        assertThat(applicationContext.getBeanNamesForType(RateLimitFilter.class)).isEmpty();
    }

    private HttpResponse<String> register(String forwardedFor) throws IOException, InterruptedException {
        return send("POST", "/api/v1/auth/register", forwardedFor, null,
                "{\"name\":\"New Person\",\"email\":\"new.person@example.com\",\"password\":\"safe-password-123\"}");
    }

    private HttpResponse<String> sendTestDigest(String bearerToken) throws IOException, InterruptedException {
        return send("POST", "/api/v1/digest/test", null, bearerToken, null);
    }

    private String regularToken(String email) {
        return jwtService.createAccessToken(new DpUser("Dev User", email, "unused", DpUserRole.MEMBER));
    }

    private HttpResponse<String> send(String method, String path, String forwardedFor, String bearerToken, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, jsonBody == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonBody));
        if (jsonBody != null) {
            request.header("Content-Type", "application/json");
        }
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, FlywayAutoConfiguration.class})
    @Import({SecurityConfig.class, RateLimiter.class, JwtAuthenticationFilter.class, JwtService.class, ApiExceptionHandler.class,
            AuthController.class, TestEndpoints.class})
    static class SecurityWebSlice {
    }

    @RestController
    static class TestEndpoints {

        @PostMapping("/api/v1/digest/test")
        String sendTest() {
            return "sent";
        }
    }
}
