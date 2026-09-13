package com.sterul.opencookbookapiserver.controllers.exceptions;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

public class UserNotActiveException extends ApiException {

    public UserNotActiveException() {
        super(ApiErrorCode.ACCOUNT_NOT_ACTIVATED);
    }
}
