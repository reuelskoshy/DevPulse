package com.devpulse.sync.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.devpulse.sync.domain.GitHubCommit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitHubCommitRepository extends JpaRepository<GitHubCommit, UUID> {

    List<GitHubCommit> findByRepository_IdAndShaIn(UUID repositoryId, Collection<String> shas);

    List<GitHubCommit> findByRepository_GithubAccountIdAndAuthoredAtAfter(
            UUID githubAccountId, Instant after, Pageable pageable);

    long countByRepository_GithubAccountId(UUID githubAccountId);
}
