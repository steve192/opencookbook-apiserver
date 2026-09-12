package com.sterul.opencookbookapiserver.services.ml;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;

/**
 * The subsystem could not be reached, or would not accept our credentials.
 *
 * Its own type because callers treat it differently from a job that failed: this one is about
 * the instance rather than about the request, so the feature switches itself off.
 */
public class MlUnavailableException extends MlSubsystemException {

    public MlUnavailableException(String internalMessage) {
        super(ApiErrorCode.SCAN_UNAVAILABLE, internalMessage);
    }

    public MlUnavailableException(String internalMessage, Throwable cause) {
        super(ApiErrorCode.SCAN_UNAVAILABLE, internalMessage, cause);
    }
}
