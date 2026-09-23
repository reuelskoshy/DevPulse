package com.devpulse.insights.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.devpulse.common.exception.ConflictException;
import com.devpulse.common.exception.NotFoundException;
import com.devpulse.common.security.UserPrincipal;
import com.devpulse.insights.api.InsightResponse;
import com.devpulse.insights.domain.Insight;
import com.devpulse.insights.persistence.InsightRepository;
import com.devpulse.integration.github.GitHubAccount;
import com.devpulse.integration.github.GitHubAccountRepository;
import com.devpulse.sync.domain.GitHubCommit;
import com.devpulse.sync.persistence.GitHubCommitRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InsightService {

    private static final Duration LOOKBACK = Duration.ofDays(14);
    private static final int MAX_COMMITS_CONSIDERED = 200;
    private static final int MAX_EXAMPLE_MESSAGES_PER_REPO = 5;
    private static final int MAX_MESSAGE_EXCERPT_LENGTH = 120;
    private static final String NO_ACTIVITY_SUMMARY =
            "No commit activity in the last 14 days. Sync GitHub and push some code to get an insight.";

    private final GitHubAccountRepository accountRepository;
    private final GitHubCommitRepository commitRepository;
    private final InsightRepository insightRepository;
    private final AnthropicClient anthropicClient;
    private final AnthropicProperties anthropicProperties;

    public InsightService(GitHubAccountRepository accountRepository, GitHubCommitRepository commitRepository,
                   InsightRepository insightRepository, AnthropicClient anthropicClient,
                   AnthropicProperties anthropicProperties) {
        this.accountRepository = accountRepository;
        this.commitRepository = commitRepository;
        this.insightRepository = insightRepository;
        this.anthropicClient = anthropicClient;
        this.anthropicProperties = anthropicProperties;
    }

    @Transactional
    public InsightResponse generate(UserPrincipal principal) {
        GitHubAccount account = accountRepository.findByUserId(principal.id())
                .orElseThrow(() -> new ConflictException("Connect your GitHub account first."));

        List<GitHubCommit> recentCommits = commitRepository.findByRepository_GithubAccountIdAndAuthoredAtAfter(
                account.getId(), Instant.now().minus(LOOKBACK),
                PageRequest.of(0, MAX_COMMITS_CONSIDERED, Sort.by(Sort.Direction.DESC, "authoredAt")));

        int repoCount = (int) recentCommits.stream().map(c -> c.getRepository().getId()).distinct().count();

        String summary;
        if (recentCommits.isEmpty()) {
            summary = NO_ACTIVITY_SUMMARY;
        } else {
            requireConfigured();
            summary = anthropicClient.summarize(buildPrompt(recentCommits));
        }

        Insight insight = new Insight(principal.id(), summary, recentCommits.size(), repoCount);
        return InsightResponse.from(insightRepository.save(insight));
    }

    @Transactional(readOnly = true)
    public InsightResponse latest(UserPrincipal principal) {
        return insightRepository.findTopByUserIdOrderByGeneratedAtDesc(principal.id())
                .map(InsightResponse::from)
                .orElseThrow(() -> new NotFoundException("No insight has been generated yet."));
    }

    String buildPrompt(List<GitHubCommit> commits) {
        Map<String, List<GitHubCommit>> byRepo = commits.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getRepository().getFullName(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        StringBuilder block = new StringBuilder();
        byRepo.forEach((repoName, repoCommits) -> {
            block.append("Repository: ").append(repoName)
                    .append(" (").append(repoCommits.size()).append(" commits)\n");
            repoCommits.stream()
                    .limit(MAX_EXAMPLE_MESSAGES_PER_REPO)
                    .forEach(c -> block.append("  - ").append(excerpt(c.getMessage())).append('\n'));
            block.append('\n');
        });

        return "You are summarizing a developer's recent GitHub activity for a dashboard widget. "
                + "Write 2-4 plain, concise sentences (no bullet points, no markdown) describing what "
                + "they've been working on, based on this commit data from the last 14 days:\n\n"
                + block
                + "\nFocus on themes and patterns, not a list of every commit.";
    }

    private String excerpt(String message) {
        if (message == null) {
            return "";
        }
        String firstLine = message.split("\n", 2)[0];
        return firstLine.length() <= MAX_MESSAGE_EXCERPT_LENGTH
                ? firstLine
                : firstLine.substring(0, MAX_MESSAGE_EXCERPT_LENGTH);
    }

    private void requireConfigured() {
        if (anthropicProperties.apiKey() == null || anthropicProperties.apiKey().isBlank()) {
            throw new ConflictException("AI insights are not configured. Set ANTHROPIC_API_KEY.");
        }
    }
}
