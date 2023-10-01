package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.account.PasswordResetLink;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetLinkRepository extends JpaRepository<PasswordResetLink, String> {
    void deleteAllByUser(CookpalUser user);
}
