package com.devpulse.digest.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import com.devpulse.common.exception.ConflictException;
import com.devpulse.common.exception.ForbiddenException;
import com.devpulse.common.security.UserPrincipal;
import com.devpulse.digest.api.WeeklyDigest;
import com.devpulse.digest.api.WeeklyDigest.Personal;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.devpulse.user.persistence.DpUserRepository;
import com.devpulse.user.persistence.DpUserRepository.DigestCandidate;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyDigestSenderTest {

    /** Monday 28 Sep 2026, 09:30 UTC: half an hour after the default round. */
    private static final Instant NOW = Instant.parse("2026-09-28T09:30:00Z");
    private static final Instant ROUND = Instant.parse("2026-09-28T08:00:00Z");
    private static final Instant LAST_WEEK = Instant.parse("2026-09-21T08:05:00Z");

    @Mock private DpUserRepository userRepository;
    @Mock private WeeklyDigestService digestService;
    @Mock private JavaMailSender mailSender;

    private final DpUser user = new DpUser("Maya Chen", "maya@example.com", "hash", DpUserRole.MANAGER);
    private final WeeklyDigestRenderer renderer = new WeeklyDigestRenderer(properties(true, "digest@example.com"));

    @BeforeEach
    void mimeMessages() {
        lenient().when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage(
                Session.getInstance(new Properties())));
    }

    @Test
    void latestRoundIsThisWeeksSendTimeOnceItHasPassedAndLastWeeksBefore() {
        WeeklyDigestSender sender = sender(properties(true, "digest@example.com"), mailSender, NOW);

        assertThat(sender.latestRound(NOW)).isEqualTo(ROUND);
        assertThat(sender.latestRound(Instant.parse("2026-09-28T07:59:59Z")))
                .isEqualTo(Instant.parse("2026-09-21T08:00:00Z"));
        assertThat(sender.latestRound(Instant.parse("2026-10-01T12:00:00Z"))).isEqualTo(ROUND);
    }

    @Test
    void claimsThenEmailsEachDueUser() throws Exception {
        UUID id = user.getId();
        when(userRepository.findDueForWeeklyDigest(ROUND)).thenReturn(List.of(candidate(id, LAST_WEEK)));
        when(userRepository.claimWeeklyDigest(id, ROUND, NOW)).thenReturn(1);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(digestService.build(new UserPrincipal(id, "maya@example.com", "MANAGER", false)))
                .thenReturn(activeDigest());

        sender(properties(true, "digest@example.com"), mailSender, NOW).run();

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getAllRecipients()[0].toString()).isEqualTo("maya@example.com");
        assertThat(sent.getValue().getFrom()[0].toString()).isEqualTo("digest@example.com");
        assertThat(sent.getValue().getSubject()).isEqualTo("Your week: 4 commits, 1 PR merged");
        verify(userRepository, never()).releaseWeeklyDigest(any(), any(), any());
    }

    @Test
    void aClaimLostToAnotherInstanceIsSkipped() {
        UUID id = user.getId();
        when(userRepository.findDueForWeeklyDigest(ROUND)).thenReturn(List.of(candidate(id, null)));
        when(userRepository.claimWeeklyDigest(id, ROUND, NOW)).thenReturn(0);

        sender(properties(true, "digest@example.com"), mailSender, NOW).run();

        verifyNoInteractions(digestService);
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void aFailedEmailReleasesTheClaimSoALaterCheckRetries() {
        UUID id = user.getId();
        when(userRepository.findDueForWeeklyDigest(ROUND)).thenReturn(List.of(candidate(id, LAST_WEEK)));
        when(userRepository.claimWeeklyDigest(id, ROUND, NOW)).thenReturn(1);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(digestService.build(any())).thenReturn(activeDigest());
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        sender(properties(true, "digest@example.com"), mailSender, NOW).run();

        verify(userRepository).releaseWeeklyDigest(id, NOW, LAST_WEEK);
    }

    @Test
    void aDigestWithNothingToReportIsClaimedButNotSent() {
        UUID id = user.getId();
        when(userRepository.findDueForWeeklyDigest(ROUND)).thenReturn(List.of(candidate(id, null)));
        when(userRepository.claimWeeklyDigest(id, ROUND, NOW)).thenReturn(1);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(digestService.build(any())).thenReturn(new WeeklyDigest(LocalDate.parse("2026-09-22"),
                LocalDate.parse("2026-09-28"), "Maya", new Personal(true, 0, 0, 0, 0, 0, null), null));

        sender(properties(true, "digest@example.com"), mailSender, NOW).run();

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(userRepository, never()).releaseWeeklyDigest(any(), any(), any());
    }

    @Test
    void aRoundMoreThanTheCatchUpWindowOldIsNotSent() {
        sender(properties(true, "digest@example.com"), mailSender, Instant.parse("2026-10-01T09:00:00Z")).run();

        verifyNoInteractions(userRepository, digestService);
    }

    @Test
    void nothingRunsWithoutAnSmtpServerASenderAddressOrWhenDisabled() {
        WeeklyDigestSender noSmtp = sender(properties(true, "digest@example.com"), null, NOW);
        WeeklyDigestSender noFrom = sender(properties(true, ""), mailSender, NOW);
        WeeklyDigestSender disabled = sender(properties(false, "digest@example.com"), mailSender, NOW);

        noSmtp.run();
        noFrom.run();
        disabled.run();

        assertThat(noSmtp.canSend()).isFalse();
        assertThat(noFrom.canSend()).isFalse();
        assertThat(disabled.canSend()).isFalse();
        verifyNoInteractions(userRepository);
    }

    @Test
    void aTestGoesOnlyToTheCallerEvenWhenEmptyAndDoesNotCountAsTheWeeksDigest() throws Exception {
        UserPrincipal me = new UserPrincipal(user.getId(), "maya@example.com", "MANAGER", false);
        when(digestService.build(me)).thenReturn(new WeeklyDigest(LocalDate.parse("2026-09-22"),
                LocalDate.parse("2026-09-28"), "Maya", new Personal(true, 0, 0, 0, 0, 0, null), null));
        WeeklyDigestSender sender = sender(properties(true, "digest@example.com"), mailSender, NOW);

        sender.sendTest(me);

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getAllRecipients()).hasSize(1);
        assertThat(sent.getValue().getAllRecipients()[0].toString()).isEqualTo("maya@example.com");
        assertThat(sent.getValue().getSubject()).startsWith("[Test] ");
        verifyNoInteractions(userRepository);
        // A second click within the cooldown is refused.
        assertThatThrownBy(() -> sender.sendTest(me)).isInstanceOf(ConflictException.class);
    }

    @Test
    void aTestIsRefusedForTheDemoWithoutSmtpAndReleasesTheCooldownOnFailure() {
        UserPrincipal me = new UserPrincipal(user.getId(), "maya@example.com", "MANAGER", false);
        assertThatThrownBy(() -> sender(properties(true, "digest@example.com"), mailSender, NOW)
                .sendTest(new UserPrincipal(user.getId(), "maya@example.com", "MANAGER", true)))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> sender(properties(true, "digest@example.com"), null, NOW).sendTest(me))
                .isInstanceOf(ConflictException.class);

        when(digestService.build(me)).thenReturn(activeDigest());
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));
        WeeklyDigestSender sender = sender(properties(true, "digest@example.com"), mailSender, NOW);
        assertThatThrownBy(() -> sender.sendTest(me)).hasMessageContaining("mail server");
        assertThatThrownBy(() -> sender.sendTest(me)).hasMessageContaining("mail server");
    }

    private WeeklyDigestSender sender(DigestProperties properties, JavaMailSender mail, Instant now) {
        return new WeeklyDigestSender(properties, userRepository, digestService, renderer, mail,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static DigestProperties properties(boolean enabled, String from) {
        return new DigestProperties(enabled, DayOfWeek.MONDAY, 8, Duration.ofHours(1), Duration.ofDays(2), 200,
                from, "https://app.example.com");
    }

    private static WeeklyDigest activeDigest() {
        return new WeeklyDigest(LocalDate.parse("2026-09-22"), LocalDate.parse("2026-09-28"), "Maya Chen",
                new Personal(true, 4, 0, 2, 1, 0, "acme/web"), null);
    }

    private static DigestCandidate candidate(UUID id, Instant sentAt) {
        return new DigestCandidate() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public Instant getWeeklyDigestSentAt() {
                return sentAt;
            }
        };
    }
}
