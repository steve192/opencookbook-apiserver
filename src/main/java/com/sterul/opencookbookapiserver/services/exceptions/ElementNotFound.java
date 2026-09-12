package com.sterul.opencookbookapiserver.services.exceptions;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

public class ElementNotFound extends ApiException {

    public ElementNotFound() {
        super(ApiErrorCode.RESOURCE_NOT_FOUND);
    }
}
