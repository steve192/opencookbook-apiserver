package com.sterul.opencookbookapiserver.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.planning.PlanningProfile;

public interface PlanningProfileRepository extends JpaRepository<PlanningProfile, Long> {

    List<PlanningProfile> findAllByOwnerOrderByName(CookpalUser owner);

    Optional<PlanningProfile> findByIdAndOwner(Long id, CookpalUser owner);
}
