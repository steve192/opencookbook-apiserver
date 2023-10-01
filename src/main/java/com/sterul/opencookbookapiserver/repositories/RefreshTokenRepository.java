package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositories.entities.RefreshToken;
import com.sterul.opencookbookapiserver.repositories.entities.account.User;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    public RefreshToken findByOwner(User owner);

    public RefreshToken findByTokenAndOwner(String token, User owner);

    public void deleteAllByOwner(User owner);

    void deleteAllByValidUntilBeforeAndOwner(Instant validUntil, User owner);

}
