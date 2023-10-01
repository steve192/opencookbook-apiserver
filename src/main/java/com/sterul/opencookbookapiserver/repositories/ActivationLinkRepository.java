package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.account.ActivationLink;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivationLinkRepository extends JpaRepository<ActivationLink, String> {
    void deleteAllByUser(CookpalUser user);
}
