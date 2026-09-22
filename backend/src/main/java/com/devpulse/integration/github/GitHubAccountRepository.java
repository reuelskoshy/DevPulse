package com.devpulse.integration.github;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface GitHubAccountRepository extends JpaRepository<GitHubAccount, UUID> {
    Optional<GitHubAccount> findByUserId(UUID userId);
}
