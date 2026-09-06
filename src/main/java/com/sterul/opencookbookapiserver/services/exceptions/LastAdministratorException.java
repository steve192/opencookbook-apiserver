package com.sterul.opencookbookapiserver.services.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Locking yourself out of the admin panel cannot be undone from the admin panel. */
@ResponseStatus(value = HttpStatus.CONFLICT)
public class LastAdministratorException extends Exception {

    public LastAdministratorException() {
        super("The instance would be left without an administrator who can sign in");
    }
}
