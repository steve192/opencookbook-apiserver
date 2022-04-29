package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.RefreshToken;
import com.sterul.opencookbookapiserver.entities.account.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    public RefreshToken findByOwner(User owner);

    public RefreshToken findByTokenAndOwner(String token, User owner);

    public void deleteAllByOwner(User owner);

    void deleteAllByValidUntilBeforeAndOwner(Instant validUntil, User owner);

}
