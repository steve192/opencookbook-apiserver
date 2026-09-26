package com.sterul.opencookbookapiserver.services;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.ReviewedRun;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

/** The one rule every reviewed run shares: each step only follows the one before it. */
public final class ReviewedRuns {

    private ReviewedRuns() {
    }

    public static <R extends ReviewedRun> R require(R run, ReviewedRun.Status status) {
        if (run.getStatus() != status) {
            throw new ApiException(ApiErrorCode.CONFLICT,
                    "Run " + run.getId() + " is " + run.getStatus() + ", not " + status);
        }
        return run;
    }

    /** Applying nothing would close the run as applied without it having done anything. */
    public static <P> List<P> requireAnyAccepted(ReviewedRun run, List<P> accepted) {
        if (accepted.isEmpty()) {
            throw new ApiException(ApiErrorCode.CONFLICT, "Accept the proposals to apply first: run " + run.getId() + " has none accepted");
        }
        return accepted;
    }
}
