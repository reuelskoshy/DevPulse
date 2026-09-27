package com.devpulse.user.config;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code devpulse.admin.emails} (env {@code ADMIN_EMAILS}, comma-separated): accounts that are always ADMIN.
 * This is how the first admin comes to exist; after that, admins can promote others from the People screen.
 */
@ConfigurationProperties(prefix = "devpulse.admin")
public record AdminProperties(@DefaultValue List<String> emails) {

    /** The configured emails, trimmed and lowercased like stored emails, blanks dropped. */
    public Set<String> normalizedEmails() {
        return emails.stream()
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdminEmail(String normalizedEmail) {
        return normalizedEmails().contains(normalizedEmail);
    }
}
