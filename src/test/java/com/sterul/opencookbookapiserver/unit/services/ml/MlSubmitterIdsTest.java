package com.sterul.opencookbookapiserver.unit.services.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.services.ml.MlSubmitterIds;
import com.sterul.opencookbookapiserver.services.ml.MlUnavailableException;

/**
 * The opaque id the subsystem groups somebody's jobs by.
 *
 * It has to be stable for as long as donated training data is kept - a changed id makes an
 * earlier donation unmatchable to a later "delete everything of mine" - and it must not be
 * reversible into the account it came from.
 */
class MlSubmitterIdsTest {

    private final OpencookbookConfiguration configuration = new OpencookbookConfiguration();
    private final MlSubmitterIds cut = new MlSubmitterIds(configuration);

    @Test
    void theSameUserAlwaysGetsTheSameId() throws MlUnavailableException {
        configuration.getMl().setApiToken("cpml_a_token");

        assertEquals(cut.of(user(7L)), cut.of(user(7L)));
    }

    @Test
    void differentPeopleGetDifferentIds() throws MlUnavailableException {
        configuration.getMl().setApiToken("cpml_a_token");

        assertNotEquals(cut.of(user(7L)), cut.of(user(8L)));
    }

    @Test
    void theIdCarriesNothingOfTheAccountItCameFrom() throws MlUnavailableException {
        configuration.getMl().setApiToken("cpml_a_token");

        var id = cut.of(user(7L));

        assertTrue(id.matches("[0-9a-f]{64}"), id);
    }

    @Test
    void anExplicitSaltSurvivesTheTokenBeingRotated() throws MlUnavailableException {
        configuration.getMl().setApiToken("cpml_the_first_token");
        configuration.getMl().setSubmitterSalt("a-salt-of-its-own");
        var before = cut.of(user(7L));

        configuration.getMl().setApiToken("cpml_the_second_token");

        assertEquals(before, cut.of(user(7L)));
    }

    @Test
    void withoutATokenOrASaltThereIsNothingToDeriveFrom() {
        // Configured but not usable, which is what an unreachable subsystem looks like too -
        // and not an internal error, which is what an unkeyed derivation would raise.
        configuration.getMl().setServiceUrl("https://ml.example.com");

        assertThrows(MlUnavailableException.class, () -> cut.of(user(7L)));
    }

    @Test
    void anApiTokenThatWasLeftOutEntirelyIsTheSameAsAnEmptyOne() {
        configuration.getMl().setApiToken(null);

        assertThrows(MlUnavailableException.class, () -> cut.of(user(7L)));
    }

    private CookpalUser user(Long userId) {
        var user = new CookpalUser();
        user.setUserId(userId);
        return user;
    }
}
