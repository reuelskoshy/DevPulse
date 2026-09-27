package com.devpulse.team.api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.devpulse.common.exception.ApiExceptionHandler;
import com.devpulse.common.security.UserPrincipal;
import com.devpulse.integration.github.GitHubAccountRepository;
import com.devpulse.sync.persistence.GitHubCommitRepository;
import com.devpulse.team.service.TeamActivityService;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.devpulse.user.persistence.DpUserRepository;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TeamActivityControllerTest {

    private static final String ENDPOINT = "/api/v1/team/activity";
    private static final Instant NOW = Instant.parse("2026-09-27T15:30:00Z");

    @Mock private DpUserRepository userRepository;
    @Mock private GitHubAccountRepository accountRepository;
    @Mock private GitHubCommitRepository commitRepository;

    private final DpUser caller = new DpUser("Mia Member", "mia@example.com", "hashed-password", DpUserRole.MEMBER);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TeamActivityService service = new TeamActivityService(
                userRepository, accountRepository, commitRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        // Mirror Spring Boot's Jackson defaults (ISO-8601 strings for java.time types).
        var converter = new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
        mockMvc = MockMvcBuilders.standaloneSetup(new TeamActivityController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(converter)
                .build();

        UserPrincipal principal = new UserPrincipal(caller.getId(), caller.getEmail(), "MEMBER");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void daysOfZeroIsA400WithTheApiErrorBody() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("days", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("days must be between 1 and 90."))
                .andExpect(jsonPath("$.fieldErrors").value(anEmptyMap()))
                .andExpect(jsonPath("$.timestamp").isString());

        verifyNoInteractions(userRepository, accountRepository, commitRepository);
    }

    @Test
    void daysOfNinetyOneIsA400() throws Exception {
        mockMvc.perform(get(ENDPOINT).param("days", "91"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("days must be between 1 and 90."));
    }

    @Test
    void defaultsToFourteenDaysAndSerializesTheContractShape() throws Exception {
        caller.setPhoneNumber("+1 555 0100");
        caller.setAddress("1 Secret Street");
        when(userRepository.findById(caller.getId())).thenReturn(Optional.of(caller));
        when(accountRepository.findSummariesByUserIdIn(anyCollection())).thenReturn(List.of());

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(14))
                .andExpect(jsonPath("$.from").value("2026-09-14"))
                .andExpect(jsonPath("$.to").value("2026-09-27"))
                .andExpect(jsonPath("$.totals.members").value(1))
                .andExpect(jsonPath("$.totals.connectedMembers").value(0))
                .andExpect(jsonPath("$.totals.activeMembers").value(0))
                .andExpect(jsonPath("$.totals.commits").value(0))
                .andExpect(jsonPath("$.totals.reposTouched").value(0))
                .andExpect(jsonPath("$.daily", hasSize(14)))
                .andExpect(jsonPath("$.daily[0].date").value("2026-09-14"))
                .andExpect(jsonPath("$.daily[0].commits").value(0))
                .andExpect(jsonPath("$.daily[13].date").value("2026-09-27"))
                .andExpect(jsonPath("$.members", hasSize(1)))
                .andExpect(jsonPath("$.members[0].userId").value(caller.getId().toString()))
                .andExpect(jsonPath("$.members[0].name").value("Mia Member"))
                .andExpect(jsonPath("$.members[0].email").value("mia@example.com"))
                .andExpect(jsonPath("$.members[0].role").value("MEMBER"))
                .andExpect(jsonPath("$.members[0].active").value(true))
                .andExpect(jsonPath("$.members[0].self").value(true))
                .andExpect(jsonPath("$.members[0].connected").value(false))
                .andExpect(jsonPath("$.members[0].githubLogin").value(nullValue()))
                .andExpect(jsonPath("$.members[0].avatarUrl").value(nullValue()))
                .andExpect(jsonPath("$.members[0].lastSyncedAt").value(nullValue()))
                .andExpect(jsonPath("$.members[0].commits").value(0))
                .andExpect(jsonPath("$.members[0].activeDays").value(0))
                .andExpect(jsonPath("$.members[0].lastCommitAt").value(nullValue()))
                .andExpect(jsonPath("$.members[0].topRepos", hasSize(0)))
                .andExpect(jsonPath("$.members[0].daily", hasSize(14)))
                .andExpect(jsonPath("$.members[0].phoneNumber").doesNotExist())
                .andExpect(jsonPath("$.members[0].address").doesNotExist())
                .andExpect(jsonPath("$.members[0].accessToken").doesNotExist());

        verifyNoInteractions(commitRepository);
    }
}
