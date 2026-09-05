package com.sterul.opencookbookapiserver.controllers.responses;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InstanceInfoResponse {
    private String termsOfService;
    private boolean sharingEnabled;

    /**
     * Whether this instance can read a recipe from a photograph. False when no machine
     * learning subsystem is configured, when scanning is switched off, and when the subsystem
     * is configured but not currently reachable - so the app can stop offering it rather than
     * letting people find out one failed scan at a time.
     */
    private boolean ocrImportEnabled;
}
