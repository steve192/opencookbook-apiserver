package com.sterul.opencookbookapiserver.services;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.account.ActivationLink;
import com.sterul.opencookbookapiserver.entities.account.Invitation;
import com.sterul.opencookbookapiserver.entities.account.PasswordResetLink;

/**
 * Where the links for activating, resetting and creating an account point, whether mailed or shown
 * to an administrator. The routes are this class's business, the host is {@link AppLinkFactory}'s.
 */
@Component
public class AccountLinkFactory {

    private final AppLinkFactory appLinkFactory;

    public AccountLinkFactory(AppLinkFactory appLinkFactory) {
        this.appLinkFactory = appLinkFactory;
    }

    public String activation(ActivationLink link) {
        return appLinkFactory.linkTo("/activateAccount?activationId=" + link.getId());
    }

    public String passwordReset(PasswordResetLink link) {
        return appLinkFactory.linkTo("/resetPassword?id=" + link.getId());
    }

    public String invitation(Invitation invitation) {
        return appLinkFactory.linkTo("/invite/" + invitation.getId());
    }
}
