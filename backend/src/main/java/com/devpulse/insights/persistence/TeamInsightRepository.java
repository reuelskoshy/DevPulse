package com.devpulse.insights.persistence;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import com.devpulse.insights.domain.TeamInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamInsightRepository extends JpaRepository<TeamInsight, UUID> {

    Optional<TeamInsight> findTopByOwnerIdOrderByGeneratedAtDesc(UUID ownerId);

    /** Bulk delete that runs immediately, without loading the rows. Callers must not pass an empty collection. */
    @Modifying
    @Query("delete from TeamInsight t where t.ownerId in :ownerIds")
    int deleteByOwnerIdIn(@Param("ownerIds") Collection<UUID> ownerIds);
}
