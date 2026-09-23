package com.devpulse.user.persistence;

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
}
