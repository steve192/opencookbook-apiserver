package com.sterul.opencookbookapiserver.services.exceptions;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

public class UserAlreadyExistsException extends ApiException {

    public UserAlreadyExistsException(String internalMessage) {
        super(ApiErrorCode.EMAIL_ALREADY_REGISTERED, internalMessage);
    }
}
