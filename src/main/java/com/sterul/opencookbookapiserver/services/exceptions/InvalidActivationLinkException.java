package com.sterul.opencookbookapiserver.services.exceptions;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

public class InvalidActivationLinkException extends ApiException {

    public InvalidActivationLinkException() {
        super(ApiErrorCode.ACTIVATION_LINK_INVALID);
    }
}
