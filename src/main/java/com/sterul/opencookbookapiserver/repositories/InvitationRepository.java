package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.sterul.opencookbookapiserver.entities.account.Invitation;

import jakarta.persistence.LockModeType;

public interface InvitationRepository extends JpaRepository<Invitation, String> {

    @EntityGraph(attributePaths = "createdBy")
    List<Invitation> findAllByExpiresAtAfterOrderByCreatedOnDesc(Instant now);

    /** Two signups with the same token queue here, and the second finds it gone. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invitation> findLockedById(String id);

    int deleteByExpiresAtBefore(Instant cutoff);
}
