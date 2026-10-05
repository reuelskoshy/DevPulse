package com.devpulse.insights.api;

import com.devpulse.common.security.UserPrincipal;
import com.devpulse.insights.service.InsightService;
import com.devpulse.insights.service.TeamInsightService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/insights")
public class InsightController {

    private final InsightService insightService;
    private final TeamInsightService teamInsightService;

    public InsightController(InsightService insightService, TeamInsightService teamInsightService) {
        this.insightService = insightService;
        this.teamInsightService = teamInsightService;
    }

    @PostMapping("/generate")
    public InsightResponse generate(@AuthenticationPrincipal UserPrincipal user) {
        return insightService.generate(user);
    }

    @GetMapping("/latest")
    public InsightResponse latest(@AuthenticationPrincipal UserPrincipal user) {
        return insightService.latest(user);
    }

    @PostMapping("/team/generate")
    public TeamInsightResponse generateTeamInsight(@AuthenticationPrincipal UserPrincipal user) {
        return teamInsightService.generate(user);
    }

    @GetMapping("/team/latest")
    public TeamInsightResponse latestTeamInsight(@AuthenticationPrincipal UserPrincipal user) {
        return teamInsightService.latest(user);
    }
}
