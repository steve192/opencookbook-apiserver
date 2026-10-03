package com.sterul.opencookbookapiserver.services;

import org.springframework.core.NestedExceptionUtils;

/** What went wrong, as an administrator should read it. */
public final class FailureReason {

    private FailureReason() {
    }

    /** The innermost cause names the actual problem, such as a refused connection or a rejected login. */
    public static String of(Exception e) {
        var cause = NestedExceptionUtils.getMostSpecificCause(e);
        var message = cause.getMessage() == null ? "" : cause.getMessage();
        if (cause == e) {
            return message.isBlank() ? e.getClass().getSimpleName() : message;
        }
        var name = cause.getClass().getSimpleName();
        return message.isBlank() ? name : name + ": " + message;
    }
}
