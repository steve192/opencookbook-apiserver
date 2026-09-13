package com.sterul.opencookbookapiserver.services.exceptions;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

public class PasswordResetLinkNotExistingException extends ApiException {

    public PasswordResetLinkNotExistingException() {
        super(ApiErrorCode.PASSWORD_RESET_LINK_INVALID);
    }
}
