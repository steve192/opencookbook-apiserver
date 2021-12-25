package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.RefreshToken;
import com.sterul.opencookbookapiserver.entities.account.User;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    public RefreshToken findByOwner(User owner);

    public RefreshToken findByTokenAndOwner(String token, User owner);

}
