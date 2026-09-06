package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;

import com.sterul.opencookbookapiserver.entities.ml.MlJob;
import com.sterul.opencookbookapiserver.entities.ml.MlJobStatus;

/** One scan, as an operator needs to see it: whose it is, how it ended, and why. */
public record AdminMlJobResponse(
        String id,
        long ownerUserId,
        String ownerEmailAddress,
        String jobType,
        MlJobStatus status,
        String remoteJobId,
        Integer queuePosition,
        String errorCode,
        String errorMessage,
        boolean errorRetryable,
        boolean countsTowardsQuota,
        boolean finished,
        boolean hasResult,
        Instant createdOn,
        Instant finishedAt) {

    public static AdminMlJobResponse fromEntity(MlJob job) {
        var owner = job.getOwner();
        return new AdminMlJobResponse(
                job.getId(),
                owner.getUserId(),
                owner.getEmailAddress(),
                job.getJobType(),
                job.getStatus(),
                job.getRemoteJobId(),
                job.getQueuePosition(),
                job.getErrorCode(),
                job.getErrorMessage(),
                job.isErrorRetryable(),
                job.isCountsTowardsQuota(),
                job.isFinished(),
                job.getResult() != null,
                job.getCreatedOn(),
                job.getFinishedAt());
    }
}
