package com.sterul.opencookbookapiserver.controllers.responses;

import com.sterul.opencookbookapiserver.entities.instance.SignupMode;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InstanceInfoResponse {
    private boolean sharingEnabled;

    private boolean householdsEnabled;

    private boolean apiKeysEnabled;

    /**
     * Whether this instance can read a recipe from a photograph. False when no machine
     * learning subsystem is configured, when scanning is switched off, and when the subsystem
     * is configured but not currently reachable - so the app can stop offering it rather than
     * letting people find out one failed scan at a time.
     */
    private boolean ocrImportEnabled;

    /** No activated administrator yet: the app stays closed until the setup in the admin panel is done. */
    private boolean setupRequired;

    private SignupMode signupMode;

    /** Without mail, signups wait for an administrator and reset links come from the admin panel. */
    private boolean mailEnabled;
}
