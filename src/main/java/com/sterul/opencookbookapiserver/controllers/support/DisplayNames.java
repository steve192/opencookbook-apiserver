package com.sterul.opencookbookapiserver.controllers.support;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

/** What one member is shown about another. */
public final class DisplayNames {

    private DisplayNames() {
    }

    /** The chosen name, or the address with its local part masked; never the address itself. */
    public static String of(CookpalUser user) {
        var chosen = user.getDisplayName();
        return chosen == null || chosen.isBlank() ? masked(user.getEmailAddress()) : chosen;
    }

    private static String masked(String emailAddress) {
        var at = emailAddress.indexOf('@');
        if (at <= 0) {
            return "…";
        }
        return emailAddress.charAt(0) + "…" + emailAddress.substring(at);
    }
}
