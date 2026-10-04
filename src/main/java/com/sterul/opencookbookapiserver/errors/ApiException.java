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

    /** Told to the caller in place of the code's message; null for everybody not trusted with it. */
    private final String reason;

    /** A link the caller can follow instead, such as the recipe a post points to; null if there is none. */
    private final String link;

    public ApiException(ApiErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public ApiException(ApiErrorCode errorCode, String internalMessage) {
        this(errorCode, internalMessage, null, null, null);
    }

    public ApiException(ApiErrorCode errorCode, String internalMessage, Throwable cause) {
        this(errorCode, internalMessage, cause, null, null);
    }

    private ApiException(ApiErrorCode errorCode, String internalMessage, Throwable cause, String reason,
            String link) {
        super(internalMessage, cause);
        this.errorCode = errorCode;
        this.reason = reason;
        this.link = link;
    }

    /** A failure whose reason the caller may read, such as a mail server's answer shown to an administrator. */
    public static ApiException withReason(ApiErrorCode errorCode, String reason, Throwable cause) {
        return new ApiException(errorCode, reason, cause, reason, null);
    }

    /** @param link where the caller can go instead; null for none */
    public static ApiException withLink(ApiErrorCode errorCode, String internalMessage, String link) {
        return new ApiException(errorCode, internalMessage, null, null, link);
    }
}
