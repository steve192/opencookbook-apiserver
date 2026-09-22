package com.sterul.opencookbookapiserver.controllers.households.responses;

import com.sterul.opencookbookapiserver.controllers.support.DisplayNames;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.HouseholdMembership;

/** @param me whether this is the caller, so leaving and removing can be told apart */
public record HouseholdMemberResponse(Long userId, String displayName, boolean shareRecipes, boolean me) {

    public static HouseholdMemberResponse of(HouseholdMembership membership, CookpalUser viewer) {
        var member = membership.getMember();
        return new HouseholdMemberResponse(member.getUserId(), DisplayNames.of(member),
                membership.isShareRecipes(), member.getUserId().equals(viewer.getUserId()));
    }
}
