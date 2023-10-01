package com.sterul.opencookbookapiserver.repositoriespostgress;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.PasswordResetLink;

public interface PGPasswordResetLinkRepository extends JpaRepository<PasswordResetLink, String> {
    void deleteAllByUser(CookpalUser user);
}
