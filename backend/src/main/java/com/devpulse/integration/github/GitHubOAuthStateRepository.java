package com.devpulse.integration.github;

import org.springframework.data.jpa.repository.JpaRepository;

interface GitHubOAuthStateRepository extends JpaRepository<GitHubOAuthState, String> {
}
