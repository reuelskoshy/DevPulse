package com.devpulse.digest.api;

import com.devpulse.common.security.UserPrincipal;
import com.devpulse.digest.service.DigestPreferencesService;
import com.devpulse.digest.service.WeeklyDigestSender;
import com.devpulse.digest.service.WeeklyDigestService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/digest")
public class DigestController {

    private final WeeklyDigestService digestService;
    private final DigestPreferencesService preferencesService;
    private final WeeklyDigestSender sender;

    public DigestController(WeeklyDigestService digestService, DigestPreferencesService preferencesService,
                            WeeklyDigestSender sender) {
        this.digestService = digestService;
        this.preferencesService = preferencesService;
        this.sender = sender;
    }

    /** This week's digest for the caller, exactly as the email would summarize it. */
    @GetMapping("/weekly")
    public WeeklyDigest getWeekly(@AuthenticationPrincipal UserPrincipal principal) {
        return digestService.build(principal);
    }

    @GetMapping("/preferences")
    public DigestPreferences getPreferences(@AuthenticationPrincipal UserPrincipal principal) {
        return preferencesService.get(principal);
    }

    @PutMapping("/preferences")
    public DigestPreferences updatePreferences(@Valid @RequestBody UpdateDigestPreferencesRequest request,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return preferencesService.update(principal, request.weeklyDigestEnabled());
    }

    /** Emails this week's digest to the caller only, right now. */
    @PostMapping("/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendTest(@AuthenticationPrincipal UserPrincipal principal) {
        sender.sendTest(principal);
    }
}
