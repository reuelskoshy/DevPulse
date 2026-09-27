package com.devpulse.demo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.devpulse.auth.api.AuthController;
import com.devpulse.auth.service.AuthenticationService;
import com.devpulse.common.exception.ApiExceptionHandler;
import com.devpulse.common.security.JwtService;
import com.devpulse.demo.DemoDataSeeder.SeedResult;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.devpulse.user.persistence.DpUserRepository;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.anEmptyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DemoLoginEndpointTest {

    private static final String ENDPOINT = "/api/v1/auth/demo";
    private static final Instant NOW = Instant.parse("2026-09-27T15:30:00Z");

    @Mock private DpUserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private DemoDataSeeder seeder;

    @Test
    void disabledDemoIsA404WithTheApiErrorBody() throws Exception {
        mockMvc(demoService(false))
                .perform(post(ENDPOINT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("The live demo isn't enabled on this server."))
                .andExpect(jsonPath("$.fieldErrors").value(anEmptyMap()))
                .andExpect(jsonPath("$.timestamp").isString());

        verifyNoInteractions(seeder, userRepository, jwtService);
    }

    @Test
    void withoutADemoServiceTheDemoIsA404() throws Exception {
        AuthenticationService service = new AuthenticationService(userRepository, passwordEncoder, jwtService);

        mockMvc(service)
                .perform(post(ENDPOINT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("The live demo isn't enabled on this server."));
    }

    @Test
    void enabledDemoSeedsThenSignsInAsTheDemoManager() throws Exception {
        DpUser manager = demoManager();
        when(seeder.seed(NOW)).thenReturn(new SeedResult(true, 6, 5, 14, 1000, 5));
        when(userRepository.findByEmail(DemoTeam.MANAGER_EMAIL)).thenReturn(Optional.of(manager));
        when(jwtService.createAccessToken(manager)).thenReturn("demo-access-token");

        mockMvc(demoService(true))
                .perform(post(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("demo-access-token"))
                .andExpect(jsonPath("$.user.id").value(manager.getId().toString()))
                .andExpect(jsonPath("$.user.email").value(DemoTeam.MANAGER_EMAIL))
                .andExpect(jsonPath("$.user.role").value("MANAGER"))
                .andExpect(jsonPath("$.user.demo").value(true));

        InOrder order = inOrder(seeder, userRepository);
        order.verify(seeder).seed(NOW);
        order.verify(userRepository).findByEmail(DemoTeam.MANAGER_EMAIL);
    }

    @Test
    void aFailedReseedStillSignsInWithTheExistingDemoData() throws Exception {
        DpUser manager = demoManager();
        when(seeder.seed(NOW)).thenThrow(new IllegalStateException("database hiccup"));
        when(userRepository.findByEmail(DemoTeam.MANAGER_EMAIL)).thenReturn(Optional.of(manager));
        when(jwtService.createAccessToken(manager)).thenReturn("demo-access-token");

        mockMvc(demoService(true))
                .perform(post(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.demo").value(true));
    }

    @Test
    void neverIssuesATokenForARealAccountThatHoldsTheManagerAddress() throws Exception {
        DpUser realAccount = new DpUser("Someone Real", DemoTeam.MANAGER_EMAIL, "hash", DpUserRole.ADMIN);
        when(seeder.seed(NOW)).thenReturn(new SeedResult(false, 0, 0, 0, 0, 0));
        when(userRepository.findByEmail(DemoTeam.MANAGER_EMAIL)).thenReturn(Optional.of(realAccount));

        mockMvc(demoService(true))
                .perform(post(ENDPOINT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(DemoService.UNAVAILABLE_MESSAGE));

        verify(jwtService, never()).createAccessToken(any());
    }

    @Test
    void neverIssuesATokenForADemoUserAtTheManagerAddressWhoIsNotAManager() throws Exception {
        DpUser demoMember = new DpUser("Maya Chen", DemoTeam.MANAGER_EMAIL, "hash", DpUserRole.MEMBER);
        demoMember.setDemo(true);
        when(seeder.seed(NOW)).thenReturn(new SeedResult(true, 6, 5, 14, 1000, 5));
        when(userRepository.findByEmail(DemoTeam.MANAGER_EMAIL)).thenReturn(Optional.of(demoMember));

        mockMvc(demoService(true))
                .perform(post(ENDPOINT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(DemoService.UNAVAILABLE_MESSAGE));

        verify(jwtService, never()).createAccessToken(any());
    }

    private DemoService demoService(boolean enabled) {
        // Runs the background reseed inline so each test sees its effect before the login is answered.
        return new DemoService(new DemoProperties(enabled), seeder, Clock.fixed(NOW, ZoneOffset.UTC), Runnable::run);
    }

    private MockMvc mockMvc(DemoService demoService) {
        return mockMvc(new AuthenticationService(userRepository, passwordEncoder, jwtService, demoService));
    }

    private MockMvc mockMvc(AuthenticationService authenticationService) {
        // Mirror Spring Boot's Jackson defaults (ISO-8601 strings for java.time types).
        var converter = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
        return MockMvcBuilders.standaloneSetup(new AuthController(authenticationService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    private static DpUser demoManager() {
        DpUser manager = new DpUser("Maya Chen", DemoTeam.MANAGER_EMAIL, "hash", DpUserRole.MANAGER);
        manager.setDemo(true);
        return manager;
    }
}
