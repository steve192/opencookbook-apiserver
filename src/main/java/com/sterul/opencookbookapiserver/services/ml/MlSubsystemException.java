package com.sterul.opencookbookapiserver.services.ml;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import lombok.Getter;

/** The subsystem refused or could not complete a request. */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
@Getter
public class MlSubsystemException extends Exception {

    private final String code;
    private final boolean retryable;

    public MlSubsystemException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public MlSubsystemException(String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }
}
