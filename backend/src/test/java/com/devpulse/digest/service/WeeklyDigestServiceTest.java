package com.devpulse.digest.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.devpulse.common.security.UserPrincipal;
import com.devpulse.digest.api.WeeklyDigest;
import com.devpulse.digest.api.WeeklyDigest.Contributor;
import com.devpulse.team.api.TeamActivityResponse;
import com.devpulse.team.api.TeamActivityResponse.Member;
import com.devpulse.team.api.TeamActivityResponse.PullRequestStats;
import com.devpulse.team.api.TeamActivityResponse.RepoCommits;
import com.devpulse.team.api.TeamActivityResponse.Totals;
import com.devpulse.team.service.TeamActivityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyDigestServiceTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-09-28");
    private static final PullRequestStats NONE = new PullRequestStats(0, 0, 0, 0, null);

    @Mock private TeamActivityService teamActivityService;
    @InjectMocks private WeeklyDigestService service;

    private final UUID selfId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();
    private final UUID quietId = UUID.randomUUID();
    private final UUID disconnectedId = UUID.randomUUID();
    private final UserPrincipal principal = new UserPrincipal(selfId, "maya@example.com", "MANAGER");

    @Test
    void comparesThisWeekWithTheOneBeforeAndSummarizesTheTeam() {
        when(teamActivityService.getActivity(principal, 7)).thenReturn(response(7, 30,
                new PullRequestStats(6, 5, 2, 7, 12.5),
                member(selfId, "Maya Chen", true, true, 10, 4, new PullRequestStats(2, 2, 0, 5, 3.0), "acme/web"),
                member(reportId, "Sam Ortiz", false, true, 20, 5, NONE, "acme/api"),
                member(quietId, "Rae Kim", false, true, 0, 0, NONE, null),
                member(disconnectedId, "Lee Park", false, false, 0, 0, NONE, null)));
        when(teamActivityService.getActivity(principal, 14)).thenReturn(response(14, 55, NONE,
                member(selfId, "Maya Chen", true, true, 18, 8, NONE, "acme/web"),
                member(reportId, "Sam Ortiz", false, true, 35, 9, NONE, "acme/api"),
                member(quietId, "Rae Kim", false, true, 2, 1, NONE, null),
                member(disconnectedId, "Lee Park", false, false, 0, 0, NONE, null)));

        WeeklyDigest digest = service.build(principal);

        assertThat(digest.from()).isEqualTo(TODAY.minusDays(6));
        assertThat(digest.to()).isEqualTo(TODAY);
        assertThat(digest.recipientName()).isEqualTo("Maya Chen");
        assertThat(digest.you()).isEqualTo(new WeeklyDigest.Personal(true, 10, 8, 4, 2, 5, "acme/web"));
        assertThat(digest.team()).isNotNull();
        assertThat(digest.team().commits()).isEqualTo(30);
        assertThat(digest.team().previousCommits()).isEqualTo(25);
        assertThat(digest.team().pullRequestsMerged()).isEqualTo(5);
        assertThat(digest.team().pullRequestsOpen()).isEqualTo(2);
        assertThat(digest.team().medianHoursToMerge()).isEqualTo(12.5);
        assertThat(digest.team().topContributors())
                .containsExactly(new Contributor("Sam Ortiz", 20), new Contributor("Maya Chen", 10));
        // Only connected teammates count as quiet; someone who never linked GitHub has nothing to measure.
        assertThat(digest.team().quietMembers()).containsExactly("Rae Kim");
        assertThat(digest.isEmpty()).isFalse();
    }

    @Test
    void someoneWhoOnlySeesThemselvesGetsNoTeamSection() {
        Member unnamed = member(selfId, "  ", true, true, 0, 0, NONE, null);
        when(teamActivityService.getActivity(principal, 7)).thenReturn(response(7, 0, NONE, unnamed));
        when(teamActivityService.getActivity(principal, 14)).thenReturn(response(14, 0, NONE, unnamed));

        WeeklyDigest digest = service.build(principal);

        assertThat(digest.team()).isNull();
        assertThat(digest.recipientName()).isEqualTo(unnamed.email());
        assertThat(digest.you().topRepo()).isNull();
        assertThat(digest.isEmpty()).isTrue();
    }

    private TeamActivityResponse response(int days, int commits, PullRequestStats pullRequests, Member... members) {
        return new TeamActivityResponse(days, TODAY.minusDays(days - 1L), TODAY,
                new Totals(members.length, members.length, members.length, commits, 1, pullRequests),
                List.of(), List.of(members));
    }

    private static Member member(UUID id, String name, boolean self, boolean connected, int commits, int activeDays,
                                 PullRequestStats pullRequests, String topRepo) {
        return new Member(id, name, id + "@example.com", "MEMBER", true, self,
                connected, connected ? "login" : null, null, null, commits, activeDays, null,
                topRepo == null ? List.of() : List.of(new RepoCommits(topRepo, commits)), List.of(), pullRequests);
    }
}
