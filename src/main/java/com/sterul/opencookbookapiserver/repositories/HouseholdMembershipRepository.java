package com.sterul.opencookbookapiserver.repositories;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.HouseholdMembership;

public interface HouseholdMembershipRepository extends JpaRepository<HouseholdMembership, Long> {

    @Query("select membership from HouseholdMembership membership join fetch membership.household "
            + "where membership.member = :member order by membership.household.name")
    List<HouseholdMembership> findAllOfMember(@Param("member") CookpalUser member);

    @Query("select membership from HouseholdMembership membership join fetch membership.member "
            + "where membership.household.id = :householdId order by membership.id")
    List<HouseholdMembership> findAllInHousehold(@Param("householdId") String householdId);

    Optional<HouseholdMembership> findByHouseholdIdAndMember(String householdId, CookpalUser member);

    Optional<HouseholdMembership> findByHouseholdIdAndMemberUserId(String householdId, Long memberUserId);

    long countByHouseholdId(String householdId);

    long countByMember(CookpalUser member);

    long countByMemberAndShareRecipesTrue(CookpalUser member);

    @Query("select membership.member.userId from HouseholdMembership membership "
            + "where membership.household.id = :householdId")
    Set<Long> findMemberIds(@Param("householdId") String householdId);

    /** Every member sharing a cookbook with a household the viewer is in. */
    @Query("select distinct sharing.member.userId from HouseholdMembership sharing "
            + "where sharing.shareRecipes and sharing.household in "
            + "(select mine.household from HouseholdMembership mine where mine.member = :viewer)")
    Set<Long> findOwnerIdsVisibleTo(@Param("viewer") CookpalUser viewer);

    @Query("select membership.member.userId from HouseholdMembership membership "
            + "where membership.household.id = :householdId and membership.shareRecipes")
    Set<Long> findSharingMemberIds(@Param("householdId") String householdId);
}
