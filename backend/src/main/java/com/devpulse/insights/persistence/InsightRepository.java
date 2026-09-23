package com.devpulse.insights.persistence;

import java.util.Optional;
import java.util.UUID;

import com.devpulse.insights.domain.Insight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InsightRepository extends JpaRepository<Insight, UUID> {

    Optional<Insight> findTopByUserIdOrderByGeneratedAtDesc(UUID userId);
}
