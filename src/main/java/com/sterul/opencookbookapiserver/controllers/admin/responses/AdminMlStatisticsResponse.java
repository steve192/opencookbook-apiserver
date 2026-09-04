package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.sterul.opencookbookapiserver.entities.ml.MlJobStatus;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminMlStatisticsResponse {

    private boolean available;
    private long totalJobs;
    private Map<MlJobStatus, Long> jobsByStatus;
    private List<Failure> recentFailures;

    @Data
    @Builder
    public static class Failure {
        private String id;
        private String jobType;
        private String code;
        private String message;
        private Instant failedAt;
    }
}
