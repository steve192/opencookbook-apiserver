package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.account.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    
    public User findByEmailAddress(String emailAddress);
}
