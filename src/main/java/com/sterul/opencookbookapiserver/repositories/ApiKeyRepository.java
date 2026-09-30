package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.sterul.opencookbookapiserver.entities.account.ApiKey;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    Optional<ApiKey> findBySecretHash(String secretHash);

    List<ApiKey> findAllByOwnerOrderByCreatedOnDesc(CookpalUser owner);

    Optional<ApiKey> findByIdAndOwner(Long id, CookpalUser owner);

    long countByOwner(CookpalUser owner);

    /** Conditional, so two requests racing past the in-memory check still write once. */
    @Modifying
    @Query("UPDATE ApiKey k SET k.lastUsedAt = :at WHERE k.id = :id AND (k.lastUsedAt IS NULL OR k.lastUsedAt < :staleBefore)")
    int touch(Long id, Instant at, Instant staleBefore);
}
