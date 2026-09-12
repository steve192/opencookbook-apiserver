package com.sterul.opencookbookapiserver.services.ml;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

/** The subsystem refused or could not complete a request. */
public class MlSubsystemException extends ApiException {

    public MlSubsystemException(ApiErrorCode errorCode, String internalMessage) {
        super(errorCode, internalMessage);
    }

    public MlSubsystemException(ApiErrorCode errorCode, String internalMessage, Throwable cause) {
        super(errorCode, internalMessage, cause);
    }
}
