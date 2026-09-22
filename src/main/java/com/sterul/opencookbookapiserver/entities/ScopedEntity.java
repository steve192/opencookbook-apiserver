package com.sterul.opencookbookapiserver.entities;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;

/** Belongs either to one person or to one household, never both and never neither. */
public interface ScopedEntity {

    CookpalUser getOwner();

    void setOwner(CookpalUser owner);

    Household getHousehold();

    void setHousehold(Household household);
}
