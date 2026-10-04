package com.sterul.opencookbookapiserver.controllers.errors;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

/**
 * The body of every failed request.
 *
 * Carries no exception message, no class name and no stack trace: what went wrong inside the
 * server is written to the log, and what the caller may know is the code. The one exception is
 * a reason that was explicitly handed over for the caller to read, see {@link ApiException#withReason}.
 *
 * @param code      what went wrong, as a stable identifier clients can translate
 * @param message   the same thing in English, for callers that do not translate
 * @param status    the http status this body is sent with
 * @param retryable whether the very same request could succeed later
 * @param fieldErrors which submitted fields were rejected, for a validation failure
 * @param link      where the caller can go instead, such as the recipe a post points to
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String code,
        String message,
        int status,
        boolean retryable,
        List<FieldError> fieldErrors,
        String link) {

    /**
     * One rejected field, named as the client sent it.
     *
     * @param field   the field that was rejected
     * @param message why, from the constraint that rejected it
     */
    public record FieldError(String field, String message) {
    }

    public static ApiErrorResponse of(ApiErrorCode code) {
        return of(code, code.getStatus().value());
    }

    /**
     * The same, for a failure the framework has already given a status to. The status being
     * sent wins, so the body never contradicts the response it travels in.
     *
     * @param code   what went wrong
     * @param status the status being answered
     * @return the body to send
     */
    public static ApiErrorResponse of(ApiErrorCode code, int status) {
        return new ApiErrorResponse(code.name(), code.getMessage(), status, code.isRetryable(),
                null, null);
    }

    /** With the reason and the link the failure carries, where it carries them. */
    public static ApiErrorResponse of(ApiException exception) {
        var code = exception.getErrorCode();
        var message = exception.getReason() == null ? code.getMessage() : exception.getReason();
        return new ApiErrorResponse(code.name(), message, code.getStatus().value(), code.isRetryable(),
                null, exception.getLink());
    }

    public static ApiErrorResponse ofFields(ApiErrorCode code, List<FieldError> fieldErrors) {
        return new ApiErrorResponse(code.name(), code.getMessage(), code.getStatus().value(),
                code.isRetryable(), fieldErrors, null);
    }
}
