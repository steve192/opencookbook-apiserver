package com.sterul.opencookbookapiserver.controllers.households.responses;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.household.HouseholdMembership;

/**
 * @param shareRecipes whether the caller's own cookbook is in this household
 * @param members      null in a listing, where only the household itself is asked about
 */
public record HouseholdResponse(String id, String name, boolean shareRecipes, long memberCount,
        List<HouseholdMemberResponse> members) {

    public static HouseholdResponse summaryOf(HouseholdMembership mine, long memberCount) {
        return new HouseholdResponse(mine.getHousehold().getId(), mine.getHousehold().getName(),
                mine.isShareRecipes(), memberCount, null);
    }

    public static HouseholdResponse detailOf(HouseholdMembership mine, List<HouseholdMembership> members) {
        var viewer = mine.getMember();
        return new HouseholdResponse(mine.getHousehold().getId(), mine.getHousehold().getName(),
                mine.isShareRecipes(), members.size(),
                members.stream().map(member -> HouseholdMemberResponse.of(member, viewer)).toList());
    }
}
