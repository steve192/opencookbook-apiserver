package com.sterul.opencookbookapiserver.services.households;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.AppLinkFactory;

/**
 * Where an invite link points. The route is this class's business, the host is
 * {@link AppLinkFactory}'s.
 */
@Component
public class HouseholdInviteLinkFactory {

    private static final String INVITE_PATH = "/household-invite/";

    private final AppLinkFactory appLinkFactory;

    public HouseholdInviteLinkFactory(AppLinkFactory appLinkFactory) {
        this.appLinkFactory = appLinkFactory;
    }

    public String linkTo(String token) {
        return appLinkFactory.linkTo(INVITE_PATH + token);
    }
}
