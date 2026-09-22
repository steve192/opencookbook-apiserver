package com.sterul.opencookbookapiserver.unit.services.households;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.services.households.HouseholdCookbookAccess;

/** Whose cookbooks a viewer may read, and whose a suggestion may draw on. */
class HouseholdCookbookAccessTest {

    private static final long VIEWER_ID = 7L;

    private final HouseholdMembershipRepository memberships = mock(HouseholdMembershipRepository.class);
    private final HouseholdCookbookAccess cut = new HouseholdCookbookAccess(memberships);

    private static CookpalUser viewer() {
        var viewer = new CookpalUser();
        viewer.setUserId(VIEWER_ID);
        return viewer;
    }

    @Test
    void readingCoversEverybodySharingWithAHouseholdYouAreIn() {
        var viewer = viewer();
        when(memberships.findOwnerIdsVisibleTo(viewer)).thenReturn(Set.of(11L, 12L));

        assertEquals(Set.of(VIEWER_ID, 11L, 12L), cut.visibleOwnerIds(viewer));
    }

    @Test
    void aPoolWithHouseholdsCoversEverythingReadable() {
        var viewer = viewer();
        when(memberships.findOwnerIdsVisibleTo(viewer)).thenReturn(Set.of(11L));

        assertEquals(Set.of(VIEWER_ID, 11L), cut.poolOwnerIds(PlanScope.of(viewer), true));
    }

    @Test
    void askingForYourOwnCookbookAloneNarrowsToYou() {
        var viewer = viewer();

        assertEquals(Set.of(VIEWER_ID), cut.poolOwnerIds(PlanScope.of(viewer), false));
        verifyNoInteractions(memberships);
    }

    @Test
    void aHouseholdPoolIsItsCookbookWhateverIsAsked() {
        var household = Household.builder().id("household").build();
        when(memberships.findSharingMemberIds("household")).thenReturn(Set.of(11L));

        assertEquals(Set.of(11L), cut.poolOwnerIds(PlanScope.of(household), false));
    }
}
