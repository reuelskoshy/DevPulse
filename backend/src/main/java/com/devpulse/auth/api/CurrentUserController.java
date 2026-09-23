package com.devpulse.auth.api;

import com.devpulse.common.exception.NotFoundException;
import com.devpulse.common.security.UserPrincipal;
import com.devpulse.user.persistence.DpUserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class CurrentUserController {

    private final DpUserRepository dpUserRepository;

    public CurrentUserController(DpUserRepository dpUserRepository) {
        this.dpUserRepository = dpUserRepository;
    }

    @GetMapping
    public UserResponse getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return dpUserRepository.findById(principal.id())
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }
}
