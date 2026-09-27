package com.devpulse.user.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.devpulse.user.domain.DpUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

@Repository
public interface DpUserRepository extends JpaRepository<DpUser, UUID> {

    Optional<DpUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<DpUser> findByParent_Id(UUID parentId);

    List<DpUser> findByEmailIn(Collection<String> emails);

    boolean existsByParent(DpUser parent);

    /** Every live-demo user; used only by the demo seeder. */
    List<DpUser> findByDemoTrue();

    /** A user due a weekly digest, with the send time to put back if their digest fails to send. */
    interface DigestCandidate {
        UUID getId();

        Instant getWeeklyDigestSentAt();
    }

    /**
     * Real, active users who want the weekly digest and haven't had one since {@code due}. Accounts created after
     * {@code due} wait for the next round, so nobody gets a digest the day they sign up.
     */
    @Query("""
            select u.id as id, u.weeklyDigestSentAt as weeklyDigestSentAt from DpUser u
            where u.demo = false and u.activeStatus = true and u.weeklyDigestEnabled = true
              and u.accountCreatedDatetime < :due
              and (u.weeklyDigestSentAt is null or u.weeklyDigestSentAt < :due)
            order by u.accountCreatedDatetime
            """)
    List<DigestCandidate> findDueForWeeklyDigest(@Param("due") Instant due);

    /**
     * Marks this round's digest as sent, but only if nobody else already did: returns 1 for the caller that wins
     * the claim and 0 otherwise, so two instances never send the same digest.
     */
    @Transactional
    @Modifying
    @Query("""
            update DpUser u set u.weeklyDigestSentAt = :now
            where u.id = :id and (u.weeklyDigestSentAt is null or u.weeklyDigestSentAt < :due)
            """)
    int claimWeeklyDigest(@Param("id") UUID id, @Param("due") Instant due, @Param("now") Instant now);

    /** Undoes a claim whose email failed, so the next check retries it. A no-op if someone has claimed it since. */
    @Transactional
    @Modifying
    @Query("""
            update DpUser u set u.weeklyDigestSentAt = :previous
            where u.id = :id and u.weeklyDigestSentAt = :claimedAt
            """)
    int releaseWeeklyDigest(@Param("id") UUID id, @Param("claimedAt") Instant claimedAt,
                            @Param("previous") Instant previous);
}
