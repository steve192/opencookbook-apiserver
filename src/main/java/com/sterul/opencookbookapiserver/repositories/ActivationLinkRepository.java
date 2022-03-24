package com.sterul.opencookbookapiserver.repositories;

import com.sterul.opencookbookapiserver.entities.account.ActivationLink;
import com.sterul.opencookbookapiserver.entities.account.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivationLinkRepository extends JpaRepository<ActivationLink, String> {
    void deleteAllByUser(User user);
}
