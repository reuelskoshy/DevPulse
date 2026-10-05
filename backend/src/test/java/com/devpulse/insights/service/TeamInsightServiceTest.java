package com.devpulse.insights.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.devpulse.common.exception.ConflictException;
import com.devpulse.common.security.UserPrincipal;
import com.devpulse.insights.api.TeamInsightDetails;
import com.devpulse.insights.api.TeamInsightResponse;
import com.devpulse.insights.persistence.TeamInsightRepository;
import com.devpulse.team.api.TeamActivityResponse;
import com.devpulse.team.api.TeamActivityResponse.Member;
import com.devpulse.team.api.TeamActivityResponse.PullRequestStats;
import com.devpulse.team.api.TeamActivityResponse.Totals;
import com.devpulse.team.service.TeamActivityService;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.devpulse.user.persistence.DpUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamInsightServiceTest {

    @Mock private DpUserRepository userRepository;
    @Mock private TeamActivityService teamActivityService;
    @Mock private TeamInsightRepository teamInsightRepository;
    @Mock private GeminiClient geminiClient;

    private TeamInsightService service(GeminiProperties properties) {
        return new TeamInsightService(userRepository, teamActivityService, teamInsightRepository, geminiClient,
                properties, new ObjectMapper());
    }

    private DpUser caller(DpUserRole role) {
        return new DpUser("Alice Manager", "alice.manager@example.com", "hash", role);
    }

    @Test
    void rejectsAMemberCaller() {
        DpUser member = caller(DpUserRole.MEMBER);
        when(userRepository.findById(member.getId())).thenReturn(Optional.of(member));
        UserPrincipal principal = new UserPrincipal(member.getId(), member.getEmail(), "MEMBER");

        assertThatThrownBy(() -> service(new GeminiProperties("test-key", "gemini-3.6-flash")).generate(principal))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only managers and admins can generate a team insight.");

        verifyNoInteractions(teamActivityService, geminiClient);
    }

    @Test
    void rejectsAManagerWithNoDirectReports() {
        DpUser manager = caller(DpUserRole.MANAGER);
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        UserPrincipal principal = new UserPrincipal(manager.getId(), manager.getEmail(), "MANAGER");
        when(teamActivityService.getActivity(principal, TeamInsightService.WINDOW_DAYS))
                .thenReturn(activityWithMembers(List.of(soloMember(manager))));

        assertThatThrownBy(() -> service(new GeminiProperties("test-key", "gemini-3.6-flash")).generate(principal))
                .isInstanceOf(ConflictException.class)
                .hasMessage("You don't have any direct reports yet.");

        verifyNoInteractions(geminiClient);
    }

    @Test
    void skipsGeminiWhenNoTeamActivity() {
        DpUser manager = caller(DpUserRole.MANAGER);
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        UserPrincipal principal = new UserPrincipal(manager.getId(), manager.getEmail(), "MANAGER");
        List<Member> members = List.of(soloMember(manager), reportMember("Bob Report", 0));
        TeamActivityResponse activity = new TeamActivityResponse(14, LocalDate.now(), LocalDate.now(),
                new Totals(2, 2, 0, 0, 0, new PullRequestStats(0, 0, 0, 0, null)), List.of(), members);
        when(teamActivityService.getActivity(principal, TeamInsightService.WINDOW_DAYS)).thenReturn(activity);
        when(teamInsightRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TeamInsightResponse response = service(new GeminiProperties("test-key", "gemini-3.6-flash")).generate(principal);

        verifyNoInteractions(geminiClient);
        assertThat(response.summary()).contains("No commit activity across the team in the last 14 days");
        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.commitCount()).isZero();
    }

    @Test
    void rejectsGenerationWhenApiKeyMissing() {
        DpUser manager = caller(DpUserRole.MANAGER);
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        UserPrincipal principal = new UserPrincipal(manager.getId(), manager.getEmail(), "MANAGER");
        List<Member> members = List.of(soloMember(manager), reportMember("Bob Report", 5));
        TeamActivityResponse activity = new TeamActivityResponse(14, LocalDate.now(), LocalDate.now(),
                new Totals(2, 2, 1, 5, 1, new PullRequestStats(0, 0, 0, 0, null)), List.of(), members);
        when(teamActivityService.getActivity(principal, TeamInsightService.WINDOW_DAYS)).thenReturn(activity);

        assertThatThrownBy(() -> service(new GeminiProperties("", "gemini-3.6-flash")).generate(principal))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(geminiClient);
    }

    @Test
    void asksGeminiForStructuredJsonBuiltFromTeamActivityAndNeverLeaksEmails() {
        DpUser manager = caller(DpUserRole.MANAGER);
        when(userRepository.findById(manager.getId())).thenReturn(Optional.of(manager));
        UserPrincipal principal = new UserPrincipal(manager.getId(), manager.getEmail(), "MANAGER");

        Member alice = member(manager.getId(), "Alice Manager", "alice.manager@example.com", true, 10, 5,
                new PullRequestStats(2, 1, 1, 3, 5.0));
        Member bob = member(UUID.randomUUID(), "Bob Report", "bob.report@example.com", false, 7, 4,
                new PullRequestStats(1, 1, 0, 2, 8.0));
        Member carol = member(UUID.randomUUID(), "Carol Report", "carol.report@example.com", false, 3, 2,
                new PullRequestStats(0, 0, 0, 1, null));
        List<Member> members = List.of(alice, bob, carol);
        TeamActivityResponse activity = new TeamActivityResponse(14, LocalDate.now().minusDays(13), LocalDate.now(),
                new Totals(3, 3, 3, 20, 4, new PullRequestStats(3, 2, 1, 6, 6.0)), List.of(), members);
        when(teamActivityService.getActivity(principal, TeamInsightService.WINDOW_DAYS)).thenReturn(activity);
        when(geminiClient.generateJson(any(), any())).thenReturn("""
                {"headline": "The team shipped steadily across four repos",
                 "overview": "The team focused on feature work and kept reviews flowing.",
                 "highlights": [{"title": "Steady delivery", "detail": "Alice Manager and Bob Report led commits."},
                                {"title": "", "detail": "dropped because it has no title"}],
                 "patterns": ["The team committed on a good spread of days.", "  "],
                 "suggestions": ["Ask Carol Report for more reviews.", "b", "c", "d"]}
                """);
        when(teamInsightRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TeamInsightResponse response = service(new GeminiProperties("test-key", "gemini-3.6-flash")).generate(principal);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateJson(promptCaptor.capture(), eq(TeamInsightService.RESPONSE_SCHEMA));
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("Alice Manager", "Bob Report", "Carol Report", "Commits: 20",
                "Team members in view: 3", "20").doesNotContain("@", "alice.manager", "bob.report", "carol.report");

        assertThat(response.summary()).isEqualTo("The team focused on feature work and kept reviews flowing.");
        assertThat(response.memberCount()).isEqualTo(3);
        assertThat(response.commitCount()).isEqualTo(20);
        TeamInsightDetails details = response.details();
        assertThat(details.headline()).isEqualTo("The team shipped steadily across four repos");
        assertThat(details.highlights()).containsExactly(
                new com.devpulse.insights.api.InsightDetails.Highlight("Steady delivery",
                        "Alice Manager and Bob Report led commits."));
        assertThat(details.patterns()).containsExactly("The team committed on a good spread of days.");
        assertThat(details.suggestions()).hasSize(TeamInsightService.MAX_LIST_ITEMS);
        assertThat(details.facts().commits()).isEqualTo(20);
        assertThat(details.facts().memberCount()).isEqualTo(3);
        assertThat(details.facts().topContributors()).extracting(TeamInsightDetails.MemberShare::name)
                .containsExactly("Alice Manager", "Bob Report", "Carol Report");
        assertThat(details.facts().topContributors()).extracting(TeamInsightDetails.MemberShare::commits)
                .containsExactly(10, 7, 3);
    }

    private static Member soloMember(DpUser caller) {
        return member(caller.getId(), caller.getName(), caller.getEmail(), true, 0, 0,
                new PullRequestStats(0, 0, 0, 0, null));
    }

    private static Member reportMember(String name, int commits) {
        return member(UUID.randomUUID(), name, name.toLowerCase().replace(' ', '.') + "@example.com", false, commits,
                commits > 0 ? 1 : 0, new PullRequestStats(0, 0, 0, 0, null));
    }

    private static Member member(UUID userId, String name, String email, boolean self, int commits, int activeDays,
                                 PullRequestStats pullRequests) {
        return new Member(userId, name, email, self ? "MANAGER" : "MEMBER", true, self, false, null, null, null,
                commits, activeDays, commits > 0 ? Instant.now() : null, List.of(), List.of(), pullRequests);
    }

    private static TeamActivityResponse activityWithMembers(List<Member> members) {
        int commits = members.stream().mapToInt(Member::commits).sum();
        int activeMembers = (int) members.stream().filter(m -> m.commits() > 0).count();
        return new TeamActivityResponse(14, LocalDate.now(), LocalDate.now(),
                new Totals(members.size(), members.size(), activeMembers, commits, 0,
                        new PullRequestStats(0, 0, 0, 0, null)),
                List.of(), members);
    }
}
