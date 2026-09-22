package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.household.Household;
import com.sterul.opencookbookapiserver.entities.household.HouseholdMembership;

public record AdminHouseholdResponse(String id, String name, Instant createdOn, int memberCount,
        List<Member> members) {

    public record Member(Long userId, String emailAddress, boolean shareRecipes) {
    }

    public static AdminHouseholdResponse fromEntity(Household household, List<HouseholdMembership> members) {
        return new AdminHouseholdResponse(household.getId(), household.getName(), household.getCreatedOn(),
                members.size(),
                members.stream()
                        .map(membership -> new Member(membership.getMember().getUserId(),
                                membership.getMember().getEmailAddress(), membership.isShareRecipes()))
                        .toList());
    }
}
