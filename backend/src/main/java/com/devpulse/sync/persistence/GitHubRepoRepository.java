package com.devpulse.sync.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.devpulse.sync.domain.GitHubRepo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitHubRepoRepository extends JpaRepository<GitHubRepo, UUID> {

    List<GitHubRepo> findByGithubAccountId(UUID githubAccountId);

    Optional<GitHubRepo> findByGithubAccountIdAndGithubRepoId(UUID githubAccountId, Long githubRepoId);

    long countByGithubAccountId(UUID githubAccountId);
}
