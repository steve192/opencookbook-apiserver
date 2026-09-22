package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.household.HouseholdInvite;

public interface HouseholdInviteRepository extends JpaRepository<HouseholdInvite, String> {

    List<HouseholdInvite> findAllByHouseholdIdAndExpiresAtAfterOrderByCreatedOnDesc(String householdId, Instant now);

    long countByHouseholdIdAndExpiresAtAfter(String householdId, Instant now);

    int deleteByExpiresAtBefore(Instant cutoff);
}
