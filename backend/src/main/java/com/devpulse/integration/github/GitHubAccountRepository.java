package com.devpulse.integration.github;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GitHubAccountRepository extends JpaRepository<GitHubAccount, UUID> {
    Optional<GitHubAccount> findByUserId(UUID userId);

    Optional<GitHubAccount> findByGithubUserId(Long githubUserId);

    /**
     * Public connection details for many users in one query. Deliberately a projection: the encrypted access
     * token is never selected, so it is neither decrypted nor at risk of leaking into a response.
     * Callers must not pass an empty collection.
     */
    @Query("""
            select a.id as id, a.userId as userId, a.login as login, a.avatarUrl as avatarUrl,
                   a.lastSyncedAt as lastSyncedAt
            from GitHubAccount a
            where a.userId in :userIds
            """)
    List<AccountSummary> findSummariesByUserIdIn(@Param("userIds") Collection<UUID> userIds);

    /**
     * When the live demo was last seeded: the seeder recreates every demo user's GitHub account, so the newest
     * {@code connectedAt} among them is the time of the last successful seed. Empty when no demo account exists.
     */
    @Query("""
            select max(a.connectedAt) from GitHubAccount a
            where a.userId in (select u.id from DpUser u where u.demo = true)
            """)
    Optional<Instant> findLatestDemoConnectedAt();

    /**
     * Users whose GitHub data is due for an automatic sync: real (never demo), active accounts that have never
     * synced or last synced before {@code cutoff}, never-synced first and then oldest first.
     */
    @Query("""
            select a.userId from GitHubAccount a
            where (a.lastSyncedAt is null or a.lastSyncedAt < :cutoff)
              and a.userId in (select u.id from DpUser u where u.demo = false and u.activeStatus = true)
            order by case when a.lastSyncedAt is null then 0 else 1 end, a.lastSyncedAt
            """)
    List<UUID> findUserIdsDueForSync(@Param("cutoff") Instant cutoff);

    interface AccountSummary {
        UUID getId();

        UUID getUserId();

        String getLogin();

        String getAvatarUrl();

        Instant getLastSyncedAt();
    }
}
