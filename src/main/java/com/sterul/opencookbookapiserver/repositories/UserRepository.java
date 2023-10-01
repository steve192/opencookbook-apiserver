package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<CookpalUser, Long> {
    
    public CookpalUser findByEmailAddress(String emailAddress);
    public Boolean existsByEmailAddress(String emailAddress);
}
