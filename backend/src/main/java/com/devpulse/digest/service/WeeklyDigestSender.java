package com.devpulse.digest.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import com.devpulse.common.security.UserPrincipal;
import com.devpulse.digest.api.WeeklyDigest;
import com.devpulse.digest.service.WeeklyDigestRenderer.RenderedEmail;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.persistence.DpUserRepository;
import com.devpulse.user.persistence.DpUserRepository.DigestCandidate;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends the weekly digest. Rather than a fixed cron, it checks every {@code check-interval} whether a round is due
 * (the latest {@code day-of-week} at {@code hour-utc}) and not more than {@code catch-up} old, so an instance that
 * was asleep at send time still delivers when it wakes.
 *
 * <p>Each recipient is claimed in the database before the email goes out, so a restart or a second instance never
 * sends the same round twice. If an email fails, the claim is released and a later check retries it. Digests with
 * nothing to report are claimed but not sent. Demo users are never selected.
 */
@Component
public class WeeklyDigestSender {

    private static final Logger log = LoggerFactory.getLogger(WeeklyDigestSender.class);

    private final DigestProperties properties;
    private final DpUserRepository userRepository;
    private final WeeklyDigestService digestService;
    private final WeeklyDigestRenderer renderer;
    private final JavaMailSender mailSender;
    private final Clock clock;

    @Autowired
    public WeeklyDigestSender(DigestProperties properties, DpUserRepository userRepository,
                              WeeklyDigestService digestService, WeeklyDigestRenderer renderer,
                              ObjectProvider<JavaMailSender> mailSender,
                              @Value("${spring.mail.host:}") String mailHost) {
        this(properties, userRepository, digestService, renderer,
                mailHost == null || mailHost.isBlank() ? null : mailSender.getIfAvailable(), Clock.systemUTC());
    }

    /** {@code mailSender} is null when no SMTP server is configured; nothing is sent then. */
    WeeklyDigestSender(DigestProperties properties, DpUserRepository userRepository, WeeklyDigestService digestService,
                       WeeklyDigestRenderer renderer, JavaMailSender mailSender, Clock clock) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.digestService = digestService;
        this.renderer = renderer;
        this.mailSender = mailSender;
        this.clock = clock;
    }

    /** Whether digests can actually be emailed: turned on, and an SMTP server and sender address configured. */
    public boolean canSend() {
        return properties.enabled() && mailSender != null && !properties.from().isBlank();
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "${devpulse.digest.check-interval:PT1H}")
    public void run() {
        if (!canSend()) {
            return;
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant due = latestRound(now);
        if (now.isAfter(due.plus(properties.catchUp()))) {
            return;
        }

        List<DigestCandidate> candidates = userRepository.findDueForWeeklyDigest(due).stream()
                .limit(Math.max(properties.batchSize(), 0))
                .toList();
        int sent = 0;
        for (DigestCandidate candidate : candidates) {
            if (userRepository.claimWeeklyDigest(candidate.getId(), due, now) == 0) {
                continue;
            }
            try {
                if (sendTo(candidate)) {
                    sent++;
                }
            } catch (RuntimeException | MessagingException exception) {
                userRepository.releaseWeeklyDigest(candidate.getId(), now, candidate.getWeeklyDigestSentAt());
                log.warn("Weekly digest failed for user {}; a later check retries it.", candidate.getId(), exception);
            }
        }
        if (!candidates.isEmpty()) {
            log.info("Weekly digest: {} sent, {} due.", sent, candidates.size());
        }
    }

    /** The most recent send time at or before {@code now}. */
    Instant latestRound(Instant now) {
        ZonedDateTime utcNow = now.atZone(ZoneOffset.UTC);
        ZonedDateTime round = utcNow.with(TemporalAdjusters.previousOrSame(properties.dayOfWeek()))
                .withHour(Math.floorMod(properties.hourUtc(), 24))
                .truncatedTo(ChronoUnit.HOURS);
        if (round.isAfter(utcNow)) {
            round = round.minusWeeks(1);
        }
        return round.toInstant();
    }

    private boolean sendTo(DigestCandidate candidate) throws MessagingException {
        Optional<DpUser> found = userRepository.findById(candidate.getId());
        if (found.isEmpty()) {
            return false;
        }
        DpUser user = found.get();
        WeeklyDigest digest = digestService.build(
                new UserPrincipal(user.getId(), user.getEmail(), user.getRole().name(), false));
        if (digest.isEmpty()) {
            return false;
        }

        RenderedEmail email = renderer.render(digest);
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.from());
        helper.setTo(user.getEmail());
        helper.setSubject(email.subject());
        helper.setText(email.text(), email.html());
        mailSender.send(message);
        return true;
    }
}
