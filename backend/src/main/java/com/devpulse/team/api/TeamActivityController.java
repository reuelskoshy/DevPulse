package com.devpulse.team.api;

import com.devpulse.common.security.UserPrincipal;
import com.devpulse.team.service.TeamActivityService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/team")
public class TeamActivityController {

    static final String DEFAULT_DAYS = "14";

    private final TeamActivityService teamActivityService;

    public TeamActivityController(TeamActivityService teamActivityService) {
        this.teamActivityService = teamActivityService;
    }

    @GetMapping("/activity")
    public TeamActivityResponse getActivity(
            @RequestParam(name = "days", defaultValue = DEFAULT_DAYS) int days,
            @AuthenticationPrincipal UserPrincipal principal) {
        return teamActivityService.getActivity(principal, days);
    }
}
