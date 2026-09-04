package com.sterul.opencookbookapiserver.controllers.ml.responses;

import com.sterul.opencookbookapiserver.controllers.responses.RecipeResponse;
import com.sterul.opencookbookapiserver.entities.ml.MlJob;
import com.sterul.opencookbookapiserver.entities.ml.MlJobStatus;
import com.sterul.opencookbookapiserver.services.ml.recipeocr.RecipeOcrResult;

import lombok.Builder;
import lombok.Data;

/** How a machine learning job looks to the app. */
@Data
@Builder
public class MlJobResponse {

    private String id;
    private String jobType;
    private MlJobStatus status;

    /** The recipe that was read, once the job is done. */
    private RecipeResponse recipe;

    /** Where each kind of content was found on the photograph. */
    private RecipeOcrResult.Blocks blocks;

    /** What is wrong with the photograph, where anything is. */
    private RecipeOcrResult.Photo photo;

    /** How many scans are ahead of this one, while it is still waiting. */
    private Integer queuePosition;

    private Error error;

    @Data
    @Builder
    public static class Error {
        private String code;
        private String message;
        private boolean retryable;
    }

    public static MlJobResponse of(MlJob job, RecipeResponse recipe,
            RecipeOcrResult.Blocks blocks, RecipeOcrResult.Photo photo) {
        return MlJobResponse.builder()
                .id(job.getId())
                .jobType(job.getJobType())
                .status(job.getStatus())
                .recipe(recipe)
                .blocks(blocks)
                .photo(photo)
                .queuePosition(job.getQueuePosition())
                .error(errorOf(job))
                .build();
    }

    private static Error errorOf(MlJob job) {
        if (job.getErrorCode() == null) {
            return null;
        }
        return Error.builder()
                .code(job.getErrorCode())
                .message(job.getErrorMessage())
                .retryable(job.isErrorRetryable())
                .build();
    }
}
