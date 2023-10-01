package com.sterul.opencookbookapiserver.repositoriespostgress;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.CookpalUser;

public interface PGUserRepository extends JpaRepository<CookpalUser, Long> {
    
    public CookpalUser findByEmailAddress(String emailAddress);
    public Boolean existsByEmailAddress(String emailAddress);
}
