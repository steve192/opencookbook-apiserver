package com.sterul.opencookbookapiserver.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sterul.opencookbookapiserver.entities.household.Household;

public interface HouseholdRepository extends JpaRepository<Household, String> {

    @Query("select household from Household household order by household.createdOn desc")
    List<Household> findAllForAdministration();
}
