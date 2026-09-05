package com.sterul.opencookbookapiserver.services.ml;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The subsystem could not be reached, or would not accept our credentials. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class MlUnavailableException extends MlSubsystemException {

    public MlUnavailableException(String code, String message, Throwable cause) {
        super(code, message, true, cause);
    }

    public MlUnavailableException(String code, String message) {
        super(code, message, true);
    }
}
