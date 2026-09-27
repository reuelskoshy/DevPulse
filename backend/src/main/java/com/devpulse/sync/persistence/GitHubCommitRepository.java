package com.devpulse.sync.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.devpulse.sync.domain.GitHubCommit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GitHubCommitRepository extends JpaRepository<GitHubCommit, UUID> {

    List<GitHubCommit> findByRepository_IdAndShaIn(UUID repositoryId, Collection<String> shas);

    List<GitHubCommit> findByRepository_GithubAccountIdAndAuthoredAtAfter(
            UUID githubAccountId, Instant after, Pageable pageable);

    long countByRepository_GithubAccountId(UUID githubAccountId);

    /**
     * One lightweight row per commit authored in [from, until) across the given GitHub accounts.
     * Callers must not pass an empty collection.
     */
    @Query("""
            select r.githubAccountId as accountId, r.fullName as repoFullName, c.authoredAt as authoredAt
            from GitHubCommit c join c.repository r
            where r.githubAccountId in :accountIds
              and c.authoredAt >= :from
              and c.authoredAt < :until
            """)
    List<CommitActivityRow> findActivityByAccountIdsBetween(
            @Param("accountIds") Collection<UUID> accountIds,
            @Param("from") Instant from,
            @Param("until") Instant until);

    interface CommitActivityRow {
        UUID getAccountId();

        String getRepoFullName();

        Instant getAuthoredAt();
    }
}
