package com.sterul.opencookbookapiserver.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositories.entities.account.User;

public interface UserRepository extends JpaRepository<User, Long> {
    
    public User findByEmailAddress(String emailAddress);
    public Boolean existsByEmailAddress(String emailAddress);
}
