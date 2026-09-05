package com.sterul.opencookbookapiserver.services.ml;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The instance's own allowance with the subsystem is spent, for everybody on it. */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class MlQuotaExceededException extends MlSubsystemException {

    public MlQuotaExceededException(String code, String message) {
        super(code, message, true);
    }
}
