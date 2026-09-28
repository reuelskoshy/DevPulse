package com.devpulse.demo;

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
import com.devpulse.common.ratelimit.RateLimiter;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Runs against a real embedded Tomcat with the real SecurityConfig, so the guard is exercised exactly where it is
 * installed in production: inside the Spring Security chain, after the JWT has been read. (A MockMvc test with the
 * filter added by hand would not catch the filter being registered in the wrong place.) No database is needed.
 */
@SpringBootTest(
        classes = DemoReadOnlyFilterSecurityTest.SecurityWebSlice.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "devpulse.security.jwt.secret=ZGV2cHVsc2UtZGVtby1yZWFkLW9ubHktdGVzdC1zaWduaW5nLWtleS0wMTIzNDU2Nzg5",
                "devpulse.security.jwt.access-token-expiration=PT1H"
        })
class DemoReadOnlyFilterSecurityTest {

    private static final String READ_ONLY_BODY =
            "{\"status\":403,\"message\":\"The demo is read-only. Create a free account to connect your own GitHub.\"}";
    private static final String REGISTER_BODY =
            "{\"name\":\"New Person\",\"email\":\"new.person@example.com\",\"password\":\"safe-password-123\"}";

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApplicationContext applicationContext;

    @MockitoBean private AuthenticationService authenticationService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void demoSessionPostToAProtectedEndpointGetsTheExactReadOnly403() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/test/write", demoToken(), "{}");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                contentType -> assertThat(contentType).startsWith("application/json"));
        assertThat(objectMapper.readTree(response.body())).isEqualTo(objectMapper.readTree(READ_ONLY_BODY));
    }

    @Test
    void demoSessionCannotDeleteOrPatchEither() throws Exception {
        assertThat(send("DELETE", "/api/v1/test/write", demoToken(), null).statusCode()).isEqualTo(403);

        HttpResponse<String> patch = send("PATCH", "/api/v1/test/write", demoToken(), "{}");
        assertThat(patch.statusCode()).isEqualTo(403);
        assertThat(objectMapper.readTree(patch.body())).isEqualTo(objectMapper.readTree(READ_ONLY_BODY));
    }

    @Test
    void demoSessionGetIsAllowed() throws Exception {
        HttpResponse<String> response = send("GET", "/api/v1/test/read", demoToken(), null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("read ok");
    }

    @Test
    void demoSessionPostToRegisterPassesThroughTheGuard() throws Exception {
        when(authenticationService.register(any())).thenReturn(new AuthResponse("new-token",
                new UserResponse(UUID.randomUUID(), "new.person@example.com", "MEMBER", null, false)));

        HttpResponse<String> response = send("POST", "/api/v1/auth/register", demoToken(), REGISTER_BODY);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(objectMapper.readTree(response.body()).get("accessToken").asText()).isEqualTo("new-token");
        verify(authenticationService).register(any());
    }

    @Test
    void demoSessionCanStartAFreshDemoSession() throws Exception {
        when(authenticationService.demoLogin()).thenReturn(new AuthResponse("demo-token",
                new UserResponse(UUID.randomUUID(), DemoTeam.MANAGER_EMAIL, "MANAGER", null, true)));

        HttpResponse<String> response = send("POST", "/api/v1/auth/demo", demoToken(), null);

        assertThat(response.statusCode()).isEqualTo(200);
        verify(authenticationService).demoLogin();
    }

    @Test
    void regularSessionPostIsNotBlockedByTheGuard() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/test/write", regularToken(), "{}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("write ok");
    }

    /**
     * A Filter bean is also registered in the servlet container's own chain, where the security context is still
     * empty, and whichever copy of a OncePerRequestFilter runs first disables the other. The slice above does not
     * component-scan, so it would not notice an added @Component; this does.
     */
    @Test
    void theGuardIsNotASpringBean() {
        assertThat(AnnotatedElementUtils.hasAnnotation(DemoReadOnlyFilter.class, Component.class)).isFalse();
        assertThat(applicationContext.getBeanNamesForType(DemoReadOnlyFilter.class)).isEmpty();
    }

    @Test
    void anonymousPostStillGetsTheAuthenticationEntryPointNotTheReadOnlyMessage() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/test/write", null, "{}");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(objectMapper.readTree(response.body()).get("message").asText()).isEqualTo("Authentication is required.");
    }

    private String demoToken() {
        DpUser demoManager = new DpUser("Maya Chen", DemoTeam.MANAGER_EMAIL, "unused", DpUserRole.MANAGER);
        demoManager.setDemo(true);
        return jwtService.createAccessToken(demoManager);
    }

    private String regularToken() {
        return jwtService.createAccessToken(new DpUser("Dev User", "dev@example.com", "unused", DpUserRole.MEMBER));
    }

    private HttpResponse<String> send(String method, String path, String bearerToken, String jsonBody)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, jsonBody == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonBody));
        if (jsonBody != null) {
            request.header("Content-Type", "application/json");
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

        @GetMapping("/api/v1/test/read")
        String read() {
            return "read ok";
        }

        @PostMapping("/api/v1/test/write")
        String write() {
            return "write ok";
        }

        @PatchMapping("/api/v1/test/write")
        String patch() {
            return "write ok";
        }

        @DeleteMapping("/api/v1/test/write")
        String delete() {
            return "write ok";
        }
    }
}
