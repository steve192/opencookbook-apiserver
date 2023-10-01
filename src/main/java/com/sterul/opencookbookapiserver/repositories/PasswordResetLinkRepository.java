package com.sterul.opencookbookapiserver.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositories.entities.account.PasswordResetLink;
import com.sterul.opencookbookapiserver.repositories.entities.account.User;

public interface PasswordResetLinkRepository extends JpaRepository<PasswordResetLink, String> {
    void deleteAllByUser(User user);
}
