package com.devpulse.team;

import com.devpulse.integration.github.GitHubAccount;
import com.devpulse.integration.github.GitHubAccountRepository;
import com.devpulse.sync.domain.GitHubCommit;
import com.devpulse.sync.domain.GitHubPullRequest;
import com.devpulse.sync.domain.GitHubRepo;
import com.devpulse.sync.persistence.GitHubCommitRepository;
import com.devpulse.sync.persistence.GitHubPullRequestRepository;
import com.devpulse.user.domain.DpUser;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The team activity endpoint relies on two hand-written JPQL projections that the Mockito tests never execute.
 * This boots Hibernate against the real entity mappings, without any database connection, and lets Spring Data
 * build the repositories, which validates every {@code @Query} (and derived query) exactly as it would at startup.
 */
class TeamActivityQueriesTest {

    private static SessionFactory sessionFactory;
    private static EntityManager entityManager;

    @BeforeAll
    static void bootHibernateWithoutADatabase() {
        sessionFactory = new Configuration()
                .addAnnotatedClass(DpUser.class)
                .addAnnotatedClass(GitHubAccount.class)
                .addAnnotatedClass(GitHubRepo.class)
                .addAnnotatedClass(GitHubCommit.class)
                .addAnnotatedClass(GitHubPullRequest.class)
                .setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect")
                .setProperty("hibernate.boot.allow_jdbc_metadata_access", "false")
                .setProperty("hibernate.hbm2ddl.auto", "none")
                .buildSessionFactory();
        entityManager = sessionFactory.createEntityManager();
    }

    @AfterAll
    static void close() {
        if (entityManager != null) {
            entityManager.close();
        }
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }

    @Test
    void commitRepositoryQueriesAreValidAgainstTheEntityModel() {
        assertThatCode(() -> new JpaRepositoryFactory(entityManager).getRepository(GitHubCommitRepository.class))
                .doesNotThrowAnyException();
    }

    @Test
    void accountRepositoryQueriesAreValidAgainstTheEntityModel() {
        assertThatCode(() -> new JpaRepositoryFactory(entityManager).getRepository(GitHubAccountRepository.class))
                .doesNotThrowAnyException();
    }

    @Test
    void pullRequestRepositoryQueriesAreValidAgainstTheEntityModel() {
        assertThatCode(() -> new JpaRepositoryFactory(entityManager).getRepository(GitHubPullRequestRepository.class))
                .doesNotThrowAnyException();
    }

    /** Negative control: proves this connection-less setup really does reject a bad query. */
    @Test
    void anInvalidQueryIsRejected() {
        assertThatThrownBy(() -> entityManager.createQuery(
                "select c.noSuchField from GitHubCommit c join c.repository r"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
