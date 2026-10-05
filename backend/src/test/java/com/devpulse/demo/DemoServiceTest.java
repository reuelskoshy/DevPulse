package com.devpulse.demo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import com.devpulse.demo.DemoDataSeeder.SeedResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoServiceTest {

    private static final Instant START = Instant.parse("2026-09-27T06:00:00Z");
    private static final SeedResult SEEDED = new SeedResult(true, 6, 5, 14, 1000, 5, 1);
    private static final SeedResult REFUSED = new SeedResult(false, 0, 0, 0, 0, 0, 0);

    @Mock private DemoDataSeeder seeder;

    private final MutableClock clock = new MutableClock(START);

    @Test
    void seedsOnStartupWhenNothingHasBeenSeededYet() {
        when(seeder.seed(START)).thenReturn(SEEDED);

        service(true, Runnable::run).seedOnStartup();

        verify(seeder).seed(START);
    }

    @Test
    void neverSeedsWhileDisabled() {
        DemoService service = service(false, Runnable::run);

        service.seedOnStartup();
        service.refreshIfStale();

        verifyNoInteractions(seeder);
    }

    @Test
    void skipsTheSeedWhenTheDatabaseWasAlreadySeededTodayUtc() {
        when(seeder.lastSeededAt()).thenReturn(Optional.of(Instant.parse("2026-09-27T00:05:00Z")));
        DemoService service = service(true, Runnable::run);

        service.seedOnStartup();
        service.refreshIfStale();

        verify(seeder, never()).seed(any());
        // The first check caches the result, so later calls do not query the database again the same day.
        verify(seeder, times(1)).lastSeededAt();
    }

    @Test
    void reseedsWhenTheLastSeedWasOnAnEarlierUtcDateEvenIfLessThanADayAgo() {
        when(seeder.lastSeededAt()).thenReturn(Optional.of(Instant.parse("2026-09-26T23:50:00Z")));
        when(seeder.seed(START)).thenReturn(SEEDED);

        service(true, Runnable::run).refreshIfStale();

        verify(seeder).seed(START);
    }

    @Test
    void reseedsOnceTheUtcDateRollsOver() {
        when(seeder.seed(any())).thenReturn(SEEDED);
        DemoService service = service(true, Runnable::run);

        service.seedOnStartup();
        clock.set(Instant.parse("2026-09-27T23:59:59Z"));
        service.refreshIfStale();
        verify(seeder, times(1)).seed(any());

        clock.set(Instant.parse("2026-09-28T00:00:00Z"));
        service.refreshIfStale();
        verify(seeder).seed(Instant.parse("2026-09-28T00:00:00Z"));
    }

    @Test
    void seedingRunsOnTheExecutorAndTheCallerReturnsImmediately() {
        List<Runnable> queued = new ArrayList<>();
        DemoService service = service(true, queued::add);

        service.refreshIfStale();

        verifyNoInteractions(seeder);
        assertThat(queued).hasSize(1);
    }

    @Test
    void onlyOneSeedIsInFlightAtATime() {
        List<Runnable> queued = new ArrayList<>();
        when(seeder.seed(any())).thenReturn(SEEDED);
        DemoService service = service(true, queued::add);

        service.refreshIfStale();
        service.refreshIfStale();
        service.refreshIfStale();
        assertThat(queued).hasSize(1);

        queued.getFirst().run();
        service.refreshIfStale();

        assertThat(queued).hasSize(1);
        verify(seeder, times(1)).seed(any());
    }

    @Test
    void aFailedSeedIsSwallowedAndRetriedWithExponentialBackoff() {
        when(seeder.seed(any())).thenThrow(new IllegalStateException("database down"));
        DemoService service = service(true, Runnable::run);

        assertThatCode(service::seedOnStartup).doesNotThrowAnyException();
        verify(seeder, times(1)).seed(any());

        // First failure: wait one minute.
        clock.advance(Duration.ofSeconds(59));
        service.refreshIfStale();
        verify(seeder, times(1)).seed(any());
        clock.advance(Duration.ofSeconds(1));
        service.refreshIfStale();
        verify(seeder, times(2)).seed(any());

        // Second failure: wait two minutes.
        clock.advance(Duration.ofMinutes(1));
        service.refreshIfStale();
        verify(seeder, times(2)).seed(any());
        clock.advance(Duration.ofMinutes(1));
        service.refreshIfStale();
        verify(seeder, times(3)).seed(any());
    }

    @Test
    void backoffIsCappedAtTheMaximum() {
        when(seeder.seed(any())).thenThrow(new IllegalStateException("database down"));
        DemoService service = service(true, Runnable::run);

        for (int attempt = 0; attempt < 12; attempt++) {
            service.refreshIfStale();
            clock.advance(DemoService.MAX_BACKOFF);
        }

        verify(seeder, times(12)).seed(any());
    }

    @Test
    void aRefusedSeedDoesNotCountAsFreshAndBacksOff() {
        when(seeder.seed(any())).thenReturn(REFUSED);
        DemoService service = service(true, Runnable::run);

        service.seedOnStartup();
        service.refreshIfStale();
        verify(seeder, times(1)).seed(any());

        clock.advance(DemoService.INITIAL_BACKOFF);
        service.refreshIfStale();
        verify(seeder, times(2)).seed(any());
    }

    @Test
    void aSuccessAfterFailuresResetsTheBackoff() {
        when(seeder.seed(any()))
                .thenThrow(new IllegalStateException("database down"))
                .thenReturn(SEEDED)
                .thenThrow(new IllegalStateException("database down again"));
        DemoService service = service(true, Runnable::run);

        service.refreshIfStale();
        clock.advance(DemoService.INITIAL_BACKOFF);
        service.refreshIfStale();
        verify(seeder, times(2)).seed(any());

        // The next day's reseed fails; the retry waits the initial backoff again, not a doubled one.
        clock.set(Instant.parse("2026-09-28T01:00:00Z"));
        service.refreshIfStale();
        clock.advance(DemoService.INITIAL_BACKOFF);
        service.refreshIfStale();
        verify(seeder, times(4)).seed(any());
    }

    private DemoService service(boolean enabled, Executor executor) {
        return new DemoService(new DemoProperties(enabled), seeder, clock, executor);
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
