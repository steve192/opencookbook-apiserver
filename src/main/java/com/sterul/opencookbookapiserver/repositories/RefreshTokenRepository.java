package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.RefreshToken;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    public RefreshToken findByOwner(CookpalUser owner);

    public RefreshToken findByTokenAndOwner(String token, CookpalUser owner);

    public void deleteAllByOwner(CookpalUser owner);

    void deleteAllByValidUntilBeforeAndOwner(Instant validUntil, CookpalUser owner);

}
