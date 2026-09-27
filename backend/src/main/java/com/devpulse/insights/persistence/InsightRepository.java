package com.devpulse.insights.persistence;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import com.devpulse.insights.domain.Insight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InsightRepository extends JpaRepository<Insight, UUID> {

    Optional<Insight> findTopByUserIdOrderByGeneratedAtDesc(UUID userId);

    /** Bulk delete that runs immediately, without loading the rows. Callers must not pass an empty collection. */
    @Modifying
    @Query("delete from Insight i where i.userId in :userIds")
    int deleteByUserIdIn(@Param("userIds") Collection<UUID> userIds);
}
