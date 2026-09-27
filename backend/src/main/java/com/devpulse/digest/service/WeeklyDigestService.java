package com.devpulse.digest.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.devpulse.common.exception.NotFoundException;
import com.devpulse.common.security.UserPrincipal;
import com.devpulse.digest.api.WeeklyDigest;
import com.devpulse.digest.api.WeeklyDigest.Contributor;
import com.devpulse.digest.api.WeeklyDigest.Personal;
import com.devpulse.digest.api.WeeklyDigest.Team;
import com.devpulse.team.api.TeamActivityResponse;
import com.devpulse.team.api.TeamActivityResponse.Member;
import com.devpulse.team.service.TeamActivityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the weekly digest from the same team activity the app shows, so a digest never reveals anyone the
 * recipient couldn't already see. The previous week is the 14-day window minus the 7-day one.
 */
@Service
public class WeeklyDigestService {

    static final int WEEK_DAYS = 7;
    static final int TOP_CONTRIBUTORS = 3;

    private final TeamActivityService teamActivityService;

    public WeeklyDigestService(TeamActivityService teamActivityService) {
        this.teamActivityService = teamActivityService;
    }

    @Transactional(readOnly = true)
    public WeeklyDigest build(UserPrincipal principal) {
        TeamActivityResponse week = teamActivityService.getActivity(principal, WEEK_DAYS);
        TeamActivityResponse fortnight = teamActivityService.getActivity(principal, WEEK_DAYS * 2);

        Map<UUID, Member> fortnightById = new HashMap<>();
        fortnight.members().forEach(member -> fortnightById.put(member.userId(), member));
        Member self = week.members().stream().filter(Member::self).findFirst()
                .orElseThrow(() -> new NotFoundException("User not found."));

        Personal you = new Personal(
                self.connected(),
                self.commits(),
                previousCommits(self, fortnightById),
                self.activeDays(),
                self.pullRequests().merged(),
                self.pullRequests().reviews(),
                self.topRepos().isEmpty() ? null : self.topRepos().get(0).fullName());

        Team team = null;
        if (week.members().size() > 1) {
            List<Contributor> top = week.members().stream()
                    .filter(member -> member.commits() > 0)
                    .sorted(Comparator.comparingInt(Member::commits).reversed())
                    .limit(TOP_CONTRIBUTORS)
                    .map(member -> new Contributor(displayName(member), member.commits()))
                    .toList();
            List<String> quiet = week.members().stream()
                    .filter(member -> !member.self() && member.active() && member.connected() && member.commits() == 0)
                    .map(WeeklyDigestService::displayName)
                    .toList();
            TeamActivityResponse.Totals totals = week.totals();
            team = new Team(
                    totals.members(),
                    totals.activeMembers(),
                    totals.commits(),
                    Math.max(0, fortnight.totals().commits() - totals.commits()),
                    totals.pullRequests().merged(),
                    totals.pullRequests().open(),
                    totals.pullRequests().medianHoursToMerge(),
                    top,
                    quiet);
        }

        return new WeeklyDigest(week.from(), week.to(), displayName(self), you, team);
    }

    private static int previousCommits(Member member, Map<UUID, Member> fortnightById) {
        Member longer = fortnightById.get(member.userId());
        return longer == null ? 0 : Math.max(0, longer.commits() - member.commits());
    }

    private static String displayName(Member member) {
        return member.name() == null || member.name().isBlank() ? member.email() : member.name().trim();
    }
}
