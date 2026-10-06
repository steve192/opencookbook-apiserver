package com.sterul.opencookbookapiserver.services;

import java.util.Locale;

/** An account is gone, and its owner is to be told once that has been committed. */
public record AccountDeletedEvent(String emailAddress, Locale language, boolean forInactivity) {
}
