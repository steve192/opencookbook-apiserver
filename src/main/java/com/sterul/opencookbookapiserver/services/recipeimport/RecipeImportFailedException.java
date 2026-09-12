package com.sterul.opencookbookapiserver.services.recipeimport;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

/** The website was reached but nothing recipe shaped came back. */
public class RecipeImportFailedException extends ApiException {

    public RecipeImportFailedException(String internalMessage) {
        super(ApiErrorCode.IMPORT_FAILED, internalMessage);
    }

    public RecipeImportFailedException(String internalMessage, Throwable cause) {
        super(ApiErrorCode.IMPORT_FAILED, internalMessage, cause);
    }
}
