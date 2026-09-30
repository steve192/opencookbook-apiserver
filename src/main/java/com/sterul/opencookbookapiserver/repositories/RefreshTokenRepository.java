package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.sterul.opencookbookapiserver.entities.RefreshToken;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findFirstBySessionId(String sessionId);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.sessionId = :sessionId")
    int deleteSession(String sessionId);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.owner = :owner")
    int deleteAllOf(CookpalUser owner);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.owner = :owner AND t.sessionId <> :sessionId")
    int deleteAllOfBut(CookpalUser owner, String sessionId);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.passwordAt = :passwordAt WHERE t.sessionId = :sessionId")
    int recordPassword(String sessionId, Instant passwordAt);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.validUntil < :now OR t.replacedAt < :replacedBefore")
    int deleteStale(Instant now, Instant replacedBefore);
}
