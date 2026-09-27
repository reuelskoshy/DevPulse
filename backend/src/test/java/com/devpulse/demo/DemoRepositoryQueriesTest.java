package com.devpulse.demo;

import com.devpulse.insights.domain.Insight;
import com.devpulse.insights.persistence.InsightRepository;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.persistence.DpUserRepository;
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
 * The seeder's repository methods (a JPQL bulk delete and two derived queries) are mocked everywhere else. This
 * boots Hibernate on the real entity mappings, without a database connection, and lets Spring Data build the
 * repositories, which validates each query exactly as application startup would.
 */
class DemoRepositoryQueriesTest {

    private static SessionFactory sessionFactory;
    private static EntityManager entityManager;

    @BeforeAll
    static void bootHibernateWithoutADatabase() {
        sessionFactory = new Configuration()
                .addAnnotatedClass(DpUser.class)
                .addAnnotatedClass(Insight.class)
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
    void userRepositoryQueriesAreValidAgainstTheEntityModel() {
        assertThatCode(() -> new JpaRepositoryFactory(entityManager).getRepository(DpUserRepository.class))
                .doesNotThrowAnyException();
    }

    @Test
    void insightRepositoryQueriesAreValidAgainstTheEntityModel() {
        assertThatCode(() -> new JpaRepositoryFactory(entityManager).getRepository(InsightRepository.class))
                .doesNotThrowAnyException();
    }

    /** Negative control: proves this connection-less setup really does reject a bad bulk delete. */
    @Test
    void anInvalidBulkDeleteIsRejected() {
        assertThatThrownBy(() -> entityManager.createQuery("delete from Insight i where i.noSuchField in :ids"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
