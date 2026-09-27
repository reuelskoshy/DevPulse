package com.devpulse.common.persistence;

import java.util.UUID;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * Base for entities whose UUID is assigned in the constructor. Spring Data treats an entity with a non-null id as
 * existing, so {@code save()} would run {@code merge()} and issue a SELECT per row before every insert. Tracking
 * newness explicitly makes {@code save()}/{@code saveAll()} call {@code persist()}, which also lets Hibernate batch
 * the inserts.
 */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {

    @Transient
    private boolean newEntity = true;

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        newEntity = false;
    }
}
