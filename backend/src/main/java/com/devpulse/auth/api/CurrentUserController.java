package com.devpulse.auth.api;

import com.devpulse.auth.persistence.UserRepository;
import com.devpulse.common.exception.NotFoundException;
import com.devpulse.common.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class CurrentUserController {

    private final UserRepository userRepository;

    public CurrentUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    public UserResponse getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return userRepository.findById(principal.id())
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }
}
