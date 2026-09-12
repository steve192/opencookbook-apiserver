package com.sterul.opencookbookapiserver.services;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

/**
 * Thrown when uploaded bytes cannot be decoded as an image. That is the caller's doing, so it
 * must not be reported as a server error - and nothing that went wrong on our side (a full disk,
 * a directory we cannot write) may be reported as this.
 */
public class IllegalFiletypeException extends ApiException {

    public IllegalFiletypeException() {
        super(ApiErrorCode.UNSUPPORTED_FILE_TYPE);
    }
}
