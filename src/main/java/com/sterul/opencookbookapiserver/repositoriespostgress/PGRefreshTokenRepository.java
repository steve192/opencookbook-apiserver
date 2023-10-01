package com.sterul.opencookbookapiserver.repositoriespostgress;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositoriespostgress.entities.RefreshToken;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.CookpalUser;

public interface PGRefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    public RefreshToken findByOwner(CookpalUser owner);

    public RefreshToken findByTokenAndOwner(String token, CookpalUser owner);

    public void deleteAllByOwner(CookpalUser owner);

    void deleteAllByValidUntilBeforeAndOwner(Instant validUntil, CookpalUser owner);

}
