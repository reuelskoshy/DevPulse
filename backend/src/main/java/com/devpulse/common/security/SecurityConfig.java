package com.devpulse.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.devpulse.demo.DemoProperties;
import com.devpulse.sync.service.AutoSyncProperties;
import com.devpulse.user.config.AdminProperties;
import com.devpulse.demo.DemoReadOnlyFilter;
import com.devpulse.insights.service.GeminiProperties;
import com.devpulse.integration.github.GitHubProperties;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({JwtProperties.class, GitHubProperties.class, GeminiProperties.class, DemoProperties.class,
        AdminProperties.class, AutoSyncProperties.class})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter, ObjectMapper objectMapper)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // The JWT is not re-read on the ERROR dispatch to /error, so without this every error
                        // status (404, 400, 500...) is replaced by the 401 entry point below.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/v1/auth/**", "/api/v1/health", "/actuator/health/**",
                                "/api/v1/integrations/github/callback").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    writeJson(response, objectMapper, Map.of("status", 401, "message", "Authentication is required."));
                }))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // Constructed here rather than declared as a bean: Spring Boot would also register a Filter bean in
                // the servlet container's chain, and that copy could mark the request as filtered before the
                // security context exists, silently disabling the read-only guard.
                .addFilterAfter(new DemoReadOnlyFilter(objectMapper), JwtAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(org.springframework.core.env.Environment environment) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(environment.getRequiredProperty("devpulse.cors.allowed-origin")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void writeJson(jakarta.servlet.http.HttpServletResponse response, ObjectMapper objectMapper, Map<String, Object> body)
            throws IOException {
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
