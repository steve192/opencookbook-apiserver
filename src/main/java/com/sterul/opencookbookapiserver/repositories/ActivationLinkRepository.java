package com.sterul.opencookbookapiserver.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.repositories.entities.account.ActivationLink;
import com.sterul.opencookbookapiserver.repositories.entities.account.User;


public interface ActivationLinkRepository extends JpaRepository<ActivationLink, String> {
    void deleteAllByUser(User user);
}
