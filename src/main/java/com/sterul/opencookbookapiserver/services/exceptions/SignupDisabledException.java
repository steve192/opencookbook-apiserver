package com.sterul.opencookbookapiserver.services.exceptions;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

public class SignupDisabledException extends ApiException {

    public SignupDisabledException() {
        super(ApiErrorCode.SIGNUP_DISABLED);
    }
}
