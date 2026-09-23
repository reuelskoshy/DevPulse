package com.devpulse.insights.api;

import com.devpulse.common.security.UserPrincipal;
import com.devpulse.insights.service.InsightService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/insights")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    @PostMapping("/generate")
    public InsightResponse generate(@AuthenticationPrincipal UserPrincipal user) {
        return insightService.generate(user);
    }

    @GetMapping("/latest")
    public InsightResponse latest(@AuthenticationPrincipal UserPrincipal user) {
        return insightService.latest(user);
    }
}
