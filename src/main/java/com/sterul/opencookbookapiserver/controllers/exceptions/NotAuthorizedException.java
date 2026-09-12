package com.sterul.opencookbookapiserver.controllers.exceptions;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

public class NotAuthorizedException extends ApiException {

    public NotAuthorizedException() {
        super(ApiErrorCode.ACCESS_DENIED);
    }
}
