package com.sterul.opencookbookapiserver.configurations.households;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.services.access.CookbookAccess;
import com.sterul.opencookbookapiserver.services.access.PersonalCookbookAccess;
import com.sterul.opencookbookapiserver.services.households.HouseholdCookbookAccess;

/** Switching households off reverts the access rule itself, not just the endpoints. */
@Configuration
public class CookbookAccessConfiguration {

    @Bean
    public CookbookAccess cookbookAccess(OpencookbookConfiguration configuration,
            HouseholdMembershipRepository memberships) {
        return configuration.getHouseholds().isEnabled()
                ? new HouseholdCookbookAccess(memberships)
                : new PersonalCookbookAccess();
    }
}
