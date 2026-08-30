package com.sterul.opencookbookapiserver.services;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when uploaded bytes cannot be decoded as an image. That is the caller's doing, so it
 * must not be reported as a server error - and nothing that went wrong on our side (a full disk,
 * a directory we cannot write) may be reported as this.
 */
@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class IllegalFiletypeException extends Exception {

}
