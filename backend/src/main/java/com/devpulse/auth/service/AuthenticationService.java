package com.devpulse.auth.service;

import java.util.List;
import java.util.Locale;

import com.devpulse.auth.api.AuthResponse;
import com.devpulse.auth.api.LoginRequest;
import com.devpulse.auth.api.RegisterRequest;
import com.devpulse.auth.api.UserResponse;
import com.devpulse.common.exception.ConflictException;
import com.devpulse.common.exception.NotFoundException;
import com.devpulse.common.exception.UnauthorizedException;
import com.devpulse.common.security.JwtService;
import com.devpulse.demo.DemoService;
import com.devpulse.demo.DemoTeam;
import com.devpulse.user.config.AdminProperties;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.devpulse.user.persistence.DpUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    static final String RESERVED_DOMAIN_MESSAGE = "This email domain is reserved.";

    private final DpUserRepository dpUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final DemoService demoService;
    private final AdminProperties adminProperties;

    @Autowired
    public AuthenticationService(DpUserRepository dpUserRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                                 DemoService demoService, AdminProperties adminProperties) {
        this.dpUserRepository = dpUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.demoService = demoService;
        this.adminProperties = adminProperties;
    }

    /** No configured admins: every new account starts as a MEMBER. */
    public AuthenticationService(DpUserRepository dpUserRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                                 DemoService demoService) {
        this(dpUserRepository, passwordEncoder, jwtService, demoService, new AdminProperties(List.of()));
    }

    /** Without a {@link DemoService} the live demo is simply unavailable. */
    public AuthenticationService(DpUserRepository dpUserRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this(dpUserRepository, passwordEncoder, jwtService, null);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (DemoTeam.isReservedEmail(email)) {
            throw new ConflictException(RESERVED_DOMAIN_MESSAGE);
        }
        if (dpUserRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }

        // Emails listed in ADMIN_EMAILS start as ADMIN, so a fresh deployment can create its first admin.
        DpUserRole role = adminProperties.isAdminEmail(email) ? DpUserRole.ADMIN : DpUserRole.MEMBER;
        DpUser user = dpUserRepository.save(new DpUser(
                request.name(), email, passwordEncoder.encode(request.password()), role));
        return responseFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        DpUser user = dpUserRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

        // Demo personas are reachable only through demoLogin(), never with a password.
        if (user.isDemo() || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password.");
        }
        return responseFor(user);
    }

    /**
     * Signs in as the demo manager, whose direct reports are the sample team. A stale demo is reseeded in the
     * background while this login is served from the existing data. Only a demo user with the MANAGER role is
     * ever signed in here, so the endpoint can never hand out a session for a real account or for a demo user
     * whose role has been changed.
     */
    @Transactional(readOnly = true)
    public AuthResponse demoLogin() {
        if (demoService == null || !demoService.isEnabled()) {
            throw new NotFoundException(DemoService.DISABLED_MESSAGE);
        }
        demoService.refreshIfStale();
        DpUser manager = dpUserRepository.findByEmail(DemoTeam.MANAGER_EMAIL)
                .filter(user -> user.isDemo() && user.getRole() == DpUserRole.MANAGER)
                .orElseThrow(() -> new ConflictException(DemoService.UNAVAILABLE_MESSAGE));
        return responseFor(manager);
    }

    private AuthResponse responseFor(DpUser user) {
        return new AuthResponse(jwtService.createAccessToken(user), UserResponse.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
