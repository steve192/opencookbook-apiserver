package com.sterul.opencookbookapiserver.errors;

import lombok.Getter;

/**
 * A failure the caller is allowed to know about.
 *
 * The code decides both what the client is told and which http status is answered, so no call
 * site has to pick a status and no two of them can disagree. The exception message stays on
 * this side: it is for the log.
 */
@Getter
public class ApiException extends Exception {

    private final ApiErrorCode errorCode;

    public ApiException(ApiErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public ApiException(ApiErrorCode errorCode, String internalMessage) {
        super(internalMessage);
        this.errorCode = errorCode;
    }

    public ApiException(ApiErrorCode errorCode, String internalMessage, Throwable cause) {
        super(internalMessage, cause);
        this.errorCode = errorCode;
    }
}
