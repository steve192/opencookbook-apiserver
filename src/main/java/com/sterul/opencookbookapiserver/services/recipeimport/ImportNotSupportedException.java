package com.sterul.opencookbookapiserver.services.recipeimport;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

public class ImportNotSupportedException extends ApiException {

    public ImportNotSupportedException() {
        super(ApiErrorCode.IMPORT_NOT_SUPPORTED);
    }
}
