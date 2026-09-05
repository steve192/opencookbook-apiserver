package com.sterul.opencookbookapiserver.services.ml;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** This user has used their allowance for today. */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class MlUserQuotaExceededException extends MlSubsystemException {

    public MlUserQuotaExceededException(int limit) {
        super("USER_QUOTA_EXCEEDED",
                "You have scanned the maximum of " + limit + " recipes for today", true);
    }
}
