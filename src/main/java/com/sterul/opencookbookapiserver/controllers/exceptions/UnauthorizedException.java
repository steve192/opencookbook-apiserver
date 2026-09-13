package com.sterul.opencookbookapiserver.controllers.exceptions;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

public class UnauthorizedException extends ApiException {

    public UnauthorizedException() {
        super(ApiErrorCode.INVALID_CREDENTIALS);
    }
}
