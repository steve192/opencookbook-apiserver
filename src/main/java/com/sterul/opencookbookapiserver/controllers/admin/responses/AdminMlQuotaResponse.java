package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/** How much of today's scan allowance each person has used. */
@Data
@Builder
public class AdminMlQuotaResponse {

    /** Scans allowed per person per day, or 0 when there is no limit. */
    private int dailyLimit;

    /** Only people who have run a scan today; everyone else has used nothing. */
    private List<Usage> users;

    @Data
    @Builder
    public static class Usage {
        private Long userId;
        private String emailAddress;
        private long used;
        private boolean exhausted;
    }
}
