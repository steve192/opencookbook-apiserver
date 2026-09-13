package com.sterul.opencookbookapiserver.services.exceptions;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

/** Locking yourself out of the admin panel cannot be undone from the admin panel. */
public class LastAdministratorException extends ApiException {

    public LastAdministratorException() {
        super(ApiErrorCode.LAST_ADMINISTRATOR);
    }
}
