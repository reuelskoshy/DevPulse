package com.devpulse.sync.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.devpulse.integration.github.GitHubAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps connected accounts fresh without anyone pressing "Sync now". Each account syncs in its own transaction
 * (through {@link GitHubSyncService#syncUser}), so a revoked token or a GitHub error on one account is logged and
 * skipped without affecting the others. Demo accounts are never selected.
 *
 * <p>Runs on the single scheduler thread, so runs never overlap within one instance. With several instances an
 * account can occasionally sync twice at once; that is harmless because commit inserts skip known SHAs.
 */
@Component
public class GitHubAutoSync {

    private static final Logger log = LoggerFactory.getLogger(GitHubAutoSync.class);

    private final AutoSyncProperties properties;
    private final GitHubAccountRepository accountRepository;
    private final GitHubSyncService syncService;
    private final Clock clock;

    @Autowired
    public GitHubAutoSync(AutoSyncProperties properties, GitHubAccountRepository accountRepository,
                          GitHubSyncService syncService) {
        this(properties, accountRepository, syncService, Clock.systemUTC());
    }

    GitHubAutoSync(AutoSyncProperties properties, GitHubAccountRepository accountRepository,
                   GitHubSyncService syncService, Clock clock) {
        this.properties = properties;
        this.accountRepository = accountRepository;
        this.syncService = syncService;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${devpulse.sync.auto.check-interval:PT15M}")
    public void run() {
        if (!properties.enabled()) {
            return;
        }
        Instant cutoff = clock.instant().minus(properties.maxAge());
        List<UUID> due = accountRepository.findUserIdsDueForSync(cutoff).stream()
                .limit(Math.max(properties.batchSize(), 0))
                .toList();
        if (due.isEmpty()) {
            return;
        }

        int synced = 0;
        for (UUID userId : due) {
            try {
                syncService.syncUser(userId);
                synced++;
            } catch (RuntimeException exception) {
                log.warn("Automatic GitHub sync failed for user {}; a later run retries it.", userId, exception);
            }
        }
        log.info("Automatic GitHub sync: {} of {} due accounts synced.", synced, due.size());
    }
}
