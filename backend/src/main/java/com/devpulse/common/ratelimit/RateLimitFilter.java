package com.devpulse.common.ratelimit;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;

import com.devpulse.common.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Throttles a handful of sensitive or costly endpoints (sign-in, the live demo, GitHub sync, AI insight
 * generation, test digests) against brute-forcing and runaway cost. See {@link RateLimitRule} for the list.
 *
 * <p>Must run inside the Spring Security chain, after {@code JwtAuthenticationFilter} has populated the security
 * context, so a {@code Key.USER} rule can read the authenticated principal. Not a Spring bean, for the same
 * reason as {@code DemoReadOnlyFilter} (see its Javadoc): a Filter bean is also registered in the servlet
 * container's own chain, where the security context doesn't exist yet, and whichever copy runs first would
 * silently disable the other. {@code SecurityConfig} constructs it directly instead.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiter rateLimiter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        for (RateLimitRule rule : RateLimitRule.values()) {
            if (rule.matcher().matches(request)) {
                OptionalLong retryAfterSeconds = rateLimiter.tryConsume(rule, keyFor(rule, request));
                if (retryAfterSeconds.isPresent()) {
                    reject(response, rule, retryAfterSeconds.getAsLong());
                    return;
                }
                break;
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String keyFor(RateLimitRule rule, HttpServletRequest request) {
        return rule.key() == RateLimitRule.Key.USER ? userKey() : clientIp(request);
    }

    private static String userKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.id().toString();
        }
        // Every Key.USER rule requires a JWT, so this is unreachable in practice; keeps a rogue rule from crashing.
        return "anonymous";
    }

    // Render sits behind a proxy, so the caller's real address is in X-Forwarded-For, not getRemoteAddr().
    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void reject(HttpServletResponse response, RateLimitRule rule, long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("message", rule.message());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
