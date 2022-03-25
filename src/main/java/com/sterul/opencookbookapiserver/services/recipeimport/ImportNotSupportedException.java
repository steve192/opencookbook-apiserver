package com.sterul.opencookbookapiserver.services.recipeimport;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
public class ImportNotSupportedException extends Exception {

}
