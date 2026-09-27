package com.devpulse.demo;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.devpulse.common.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Makes demo sessions read-only: a request whose JWT carries {@code demo=true} may only use GET, HEAD and OPTIONS,
 * except under {@code /api/v1/auth/}, and anything else is answered with a 403 here without reaching a controller.
 *
 * <p>Must run inside the Spring Security chain, after {@code JwtAuthenticationFilter} has populated the security
 * context. It is deliberately NOT a Spring bean: Spring Boot registers every {@code Filter} bean in the servlet
 * container's own filter chain as well, where this filter would see an empty security context, and because it is
 * a {@link OncePerRequestFilter} whichever copy runs first marks the request as filtered and the other copy is
 * skipped. {@code SecurityConfig} therefore constructs it directly.
 */
public class DemoReadOnlyFilter extends OncePerRequestFilter {

    public static final String READ_ONLY_MESSAGE =
            "The demo is read-only. Create a free account to connect your own GitHub.";

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    // The same paths SecurityConfig opens with permitAll("/api/v1/auth/**"), matched on the decoded, normalized path.
    private static final RequestMatcher AUTH_ENDPOINTS = PathPatternRequestMatcher.withDefaults().matcher("/api/v1/auth/**");

    private final ObjectMapper objectMapper;

    public DemoReadOnlyFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isDemoSession() && !READ_METHODS.contains(request.getMethod()) && !AUTH_ENDPOINTS.matches(request)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", HttpStatus.FORBIDDEN.value());
            body.put("message", READ_ONLY_MESSAGE);
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isDemoSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal && principal.demo();
    }
}
