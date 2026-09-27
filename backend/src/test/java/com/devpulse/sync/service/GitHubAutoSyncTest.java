package com.devpulse.sync.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.devpulse.integration.github.GitHubAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubAutoSyncTest {

    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    @Mock private GitHubAccountRepository accountRepository;
    @Mock private GitHubSyncService syncService;

    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();

    @Test
    void doesNothingWhenDisabled() {
        autoSync(false, 25).run();

        verifyNoInteractions(accountRepository, syncService);
    }

    @Test
    void asksForAccountsLastSyncedBeforeNowMinusMaxAge() {
        when(accountRepository.findUserIdsDueForSync(NOW.minus(Duration.ofHours(6)))).thenReturn(List.of());

        autoSync(true, 25).run();

        verify(syncService, never()).syncUser(any());
    }

    @Test
    void syncsAtMostOneBatchPerRun() {
        when(accountRepository.findUserIdsDueForSync(any())).thenReturn(List.of(first, second, third));

        autoSync(true, 2).run();

        verify(syncService).syncUser(first);
        verify(syncService).syncUser(second);
        verify(syncService, never()).syncUser(third);
    }

    @Test
    void oneFailingAccountDoesNotStopTheOthers() {
        when(accountRepository.findUserIdsDueForSync(any())).thenReturn(List.of(first, second));
        when(syncService.syncUser(first)).thenThrow(new IllegalStateException("token revoked"));

        autoSync(true, 25).run();

        verify(syncService).syncUser(second);
    }

    private GitHubAutoSync autoSync(boolean enabled, int batchSize) {
        AutoSyncProperties properties =
                new AutoSyncProperties(enabled, Duration.ofHours(6), Duration.ofMinutes(15), batchSize);
        return new GitHubAutoSync(properties, accountRepository, syncService, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
