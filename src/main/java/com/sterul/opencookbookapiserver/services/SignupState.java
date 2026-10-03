package com.sterul.opencookbookapiserver.services;

/** Where a new account stands right after signing up. */
public enum SignupState {
    /** Invited: usable straight away. */
    ACTIVE,
    /** Waiting for the address to be confirmed through the mailed link. */
    AWAITING_CONFIRMATION,
    /** No mail on this instance: waiting for an administrator to activate it. */
    AWAITING_APPROVAL
}
