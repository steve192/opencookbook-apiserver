package com.sterul.opencookbookapiserver.controllers.errors;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;

/**
 * The body of every failed request.
 *
 * Carries no exception message, no class name and no stack trace: what went wrong inside the
 * server is written to the log, and what the caller may know is the code.
 *
 * @param code      what went wrong, as a stable identifier clients can translate
 * @param message   the same thing in English, for callers that do not translate
 * @param status    the http status this body is sent with
 * @param retryable whether the very same request could succeed later
 * @param fieldErrors which submitted fields were rejected, for a validation failure
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String code,
        String message,
        int status,
        boolean retryable,
        List<FieldError> fieldErrors) {

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
                null);
    }

    public static ApiErrorResponse ofFields(ApiErrorCode code, List<FieldError> fieldErrors) {
        return new ApiErrorResponse(code.name(), code.getMessage(), code.getStatus().value(),
                code.isRetryable(), fieldErrors);
    }
}
