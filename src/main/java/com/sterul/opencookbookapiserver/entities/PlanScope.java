package com.sterul.opencookbookapiserver.entities;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;

/**
 * Whose weekplan, profile or draft something is: a person's own, or a household's.
 *
 * Exactly one of {@link #owner()} and {@link #household()} is set. Repositories match on both, and
 * Spring Data turns the null one into {@code IS NULL}.
 */
public sealed interface PlanScope {

    CookpalUser owner();

    Household household();

    record Personal(CookpalUser owner) implements PlanScope {
        @Override
        public Household household() {
            return null;
        }
    }

    record OfHousehold(Household household) implements PlanScope {
        @Override
        public CookpalUser owner() {
            return null;
        }
    }

    static PlanScope of(CookpalUser user) {
        return new Personal(user);
    }

    static PlanScope of(Household household) {
        return new OfHousehold(household);
    }

    /** Which of the two an already stored row belongs to. */
    static PlanScope of(ScopedEntity entity) {
        return entity.getHousehold() == null ? of(entity.getOwner()) : of(entity.getHousehold());
    }

    /** Stamps a new row with this scope. */
    default void assignTo(ScopedEntity entity) {
        entity.setOwner(owner());
        entity.setHousehold(household());
    }
}
