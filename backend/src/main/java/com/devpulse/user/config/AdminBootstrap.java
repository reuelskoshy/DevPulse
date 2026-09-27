package com.devpulse.user.config;

import java.util.Set;

import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.devpulse.user.persistence.DpUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promotes every existing account listed in {@link AdminProperties} to ADMIN at startup; registration handles
 * accounts created later. Demo users are never promoted. The new role applies from the user's next sign-in,
 * because the role travels in the access token.
 */
@Component
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminProperties properties;
    private final DpUserRepository userRepository;

    public AdminBootstrap(AdminProperties properties, DpUserRepository userRepository) {
        this.properties = properties;
        this.userRepository = userRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void promoteConfiguredAdmins() {
        Set<String> emails = properties.normalizedEmails();
        if (emails.isEmpty()) {
            return;
        }
        for (DpUser user : userRepository.findByEmailIn(emails)) {
            if (!user.isDemo() && user.getRole() != DpUserRole.ADMIN) {
                user.setRole(DpUserRole.ADMIN);
                user.setParent(null);
                log.info("Promoted user {} to ADMIN (listed in ADMIN_EMAILS).", user.getId());
            }
        }
    }
}
