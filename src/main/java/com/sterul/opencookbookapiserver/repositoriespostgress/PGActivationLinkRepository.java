package com.sterul.opencookbookapiserver.repositoriespostgress;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.ActivationLink;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.CookpalUser;

public interface PGActivationLinkRepository extends JpaRepository<ActivationLink, String> {
    void deleteAllByUser(CookpalUser user);
}
