package com.sterul.opencookbookapiserver.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.planning.PlanDraft;
import com.sterul.opencookbookapiserver.entities.planning.PlanningProfile;

public interface PlanDraftRepository extends JpaRepository<PlanDraft, Long> {

    /** Somebody else's draft is simply not found, so ids cannot be probed. */
    Optional<PlanDraft> findByIdAndOwner(Long id, CookpalUser owner);

    List<PlanDraft> findAllByProfileAndStatus(PlanningProfile profile, PlanDraft.Status status);
}
