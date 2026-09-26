package com.sterul.opencookbookapiserver.errors;

import lombok.Getter;

/**
 * A failure the caller is allowed to know about.
 *
 * The code decides both what the client is told and which http status is answered, so no call
 * site has to pick a status and no two of them can disagree. The exception message stays on
 * this side: it is for the log.
 *
 * Unchecked on purpose. Every one of these is answered in one place, by ApiExceptionHandler,
 * and almost nowhere else: declaring it up through each layer only added ceremony that no
 * caller acted on. It also makes Spring's own rollback rule the right one, since a transaction
 * rolls back on an unchecked exception without being told to.
 */
@Getter
public class ApiException extends RuntimeException {

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
