package com.sterul.opencookbookapiserver.services.sharing;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.AppLinkFactory;

/**
 * Where a share link points. The route is this class's business, the host is
 * {@link AppLinkFactory}'s.
 */
@Component
public class ShareLinkFactory {

    @SuppressWarnings("java:S1075") // The app's own route; the host comes from AppLinkFactory.
    private static final String SHARE_PATH = "/share/";

    private final AppLinkFactory appLinkFactory;

    public ShareLinkFactory(AppLinkFactory appLinkFactory) {
        this.appLinkFactory = appLinkFactory;
    }

    public String linkTo(String shareId) {
        return appLinkFactory.linkTo(SHARE_PATH + shareId);
    }
}
