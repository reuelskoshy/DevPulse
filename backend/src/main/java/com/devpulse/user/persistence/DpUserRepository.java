package com.devpulse.user.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.devpulse.user.domain.DpUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DpUserRepository extends JpaRepository<DpUser, UUID> {

    Optional<DpUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<DpUser> findByParent_Id(UUID parentId);

    List<DpUser> findByEmailIn(Collection<String> emails);

    boolean existsByParent(DpUser parent);

    /** Every live-demo user; used only by the demo seeder. */
    List<DpUser> findByDemoTrue();
}
