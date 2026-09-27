package com.devpulse.demo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.devpulse.demo.DemoDataSeeder.SeedResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Keeps the live demo's data fresh so its history always ends today (UTC). The data counts as fresh when the last
 * seed recorded in the database happened on the current UTC date, so a restart or a second instance does not
 * reseed data that is already current.
 *
 * <p>Seeding never runs on a request thread: a stale check hands the work to a single background thread and
 * returns at once, so demo logins keep being served from the existing (committed) data while a reseed runs. Only
 * one seed runs at a time, and failures back off exponentially from {@link #INITIAL_BACKOFF} up to
 * {@link #MAX_BACKOFF}. Nothing here ever runs while the demo is disabled.
 */
@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    public static final String DISABLED_MESSAGE = "The live demo isn't enabled on this server.";
    public static final String UNAVAILABLE_MESSAGE = "The live demo is temporarily unavailable. Please try again in a minute.";
    static final Duration INITIAL_BACKOFF = Duration.ofMinutes(1);
    static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final DemoProperties properties;
    private final DemoDataSeeder seeder;
    private final Clock clock;
    private final Executor executor;
    private final AtomicBoolean seeding = new AtomicBoolean();
    /** The UTC date the demo data is known to be current for; a cache in front of the database check. */
    private volatile LocalDate freshOn;
    private volatile Instant nextAttemptAt = Instant.MIN;
    /** Only touched by the seeding thread while {@link #seeding} is held. */
    private int consecutiveFailures;

    @Autowired
    public DemoService(DemoProperties properties, DemoDataSeeder seeder) {
        this(properties, seeder, Clock.systemUTC(), Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "demo-seeder");
            thread.setDaemon(true);
            return thread;
        }));
    }

    /** Lets tests control time and run seeds inline; production uses the system UTC clock and a daemon thread. */
    public DemoService(DemoProperties properties, DemoDataSeeder seeder, Clock clock, Executor executor) {
        this.properties = properties;
        this.seeder = seeder;
        this.clock = clock;
        this.executor = executor;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        refreshIfStale();
    }

    /**
     * Starts a background reseed when the data may not be current for today, unless one is already running or a
     * failed attempt is still backing off. Never blocks on the seed and never throws.
     */
    public void refreshIfStale() {
        if (!isEnabled() || today().equals(freshOn) || clock.instant().isBefore(nextAttemptAt)) {
            return;
        }
        if (!seeding.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::reseed);
        } catch (RejectedExecutionException exception) {
            seeding.set(false);
            log.warn("Could not schedule a live demo reseed; the next demo login retries.", exception);
        }
    }

    private void reseed() {
        try {
            LocalDate today = today();
            Optional<Instant> lastSeededAt = seeder.lastSeededAt();
            if (lastSeededAt.isPresent() && utcDate(lastSeededAt.get()).equals(today)) {
                markFresh(today);
                return;
            }

            Instant startedAt = clock.instant();
            long startedNanos = System.nanoTime();
            SeedResult result = seeder.seed(startedAt);
            if (!result.seeded()) {
                backOff("the seed was refused", null);
                return;
            }
            markFresh(utcDate(startedAt));
            log.info("Seeded the live demo: {} users, {} GitHub accounts, {} repos, {} commits, {} insights in {} ms.",
                    result.users(), result.accounts(), result.repos(), result.commits(), result.insights(),
                    Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        } catch (RuntimeException exception) {
            backOff("seeding failed", exception);
        } finally {
            seeding.set(false);
        }
    }

    private void markFresh(LocalDate date) {
        freshOn = date;
        consecutiveFailures = 0;
        nextAttemptAt = Instant.MIN;
    }

    private void backOff(String reason, RuntimeException exception) {
        consecutiveFailures++;
        Duration delay = INITIAL_BACKOFF.multipliedBy(1L << Math.min(consecutiveFailures - 1, 10));
        if (delay.compareTo(MAX_BACKOFF) > 0) {
            delay = MAX_BACKOFF;
        }
        nextAttemptAt = clock.instant().plus(delay);
        log.error("Live demo reseed: {} (attempt {}); existing demo data (if any) is still served and the next "
                + "demo login after {} retries.", reason, consecutiveFailures, nextAttemptAt, exception);
    }

    private LocalDate today() {
        return utcDate(clock.instant());
    }

    private static LocalDate utcDate(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }

    @PreDestroy
    void shutdown() {
        if (executor instanceof ExecutorService service) {
            service.shutdownNow();
        }
    }
}
