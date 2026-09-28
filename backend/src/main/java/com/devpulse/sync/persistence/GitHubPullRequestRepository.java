package com.devpulse.sync.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.devpulse.sync.domain.GitHubPullRequest;
import com.devpulse.sync.domain.GitHubPullRequest.Relation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GitHubPullRequestRepository extends JpaRepository<GitHubPullRequest, UUID> {

    List<GitHubPullRequest> findByGithubAccountIdAndRelationAndGithubPrIdIn(
            UUID githubAccountId, Relation relation, Collection<Long> githubPrIds);

    /**
     * Every pull request row that can count toward a window starting at {@code from}: opened, merged or reviewed
     * since then, or an authored PR that is still open. Callers apply the window's end themselves and must not pass
     * an empty collection.
     */
    @Query("""
            select p.githubAccountId as accountId, p.relation as relation, p.openedAt as openedAt,
                   p.mergedAt as mergedAt, p.closedAt as closedAt, p.reviewedAt as reviewedAt
            from GitHubPullRequest p
            where p.githubAccountId in :accountIds
              and (p.openedAt >= :from
                   or p.mergedAt >= :from
                   or p.reviewedAt >= :from
                   or (p.relation = com.devpulse.sync.domain.GitHubPullRequest.Relation.AUTHORED
                       and p.mergedAt is null and p.closedAt is null))
            """)
    List<PullRequestActivityRow> findActivityByAccountIdsSince(
            @Param("accountIds") Collection<UUID> accountIds,
            @Param("from") Instant from);

    /**
     * One account's pull request rows that count toward a window starting at {@code from}, as
     * {@link #findActivityByAccountIdsSince} selects them, newest first.
     */
    @Query("""
            select p from GitHubPullRequest p
            where p.githubAccountId = :accountId
              and (p.openedAt >= :from
                   or p.mergedAt >= :from
                   or p.reviewedAt >= :from
                   or (p.relation = com.devpulse.sync.domain.GitHubPullRequest.Relation.AUTHORED
                       and p.mergedAt is null and p.closedAt is null))
            order by p.openedAt desc
            """)
    List<GitHubPullRequest> findForInsight(@Param("accountId") UUID accountId, @Param("from") Instant from);

    interface PullRequestActivityRow {
        UUID getAccountId();

        Relation getRelation();

        Instant getOpenedAt();

        Instant getMergedAt();

        Instant getClosedAt();

        Instant getReviewedAt();
    }
}
