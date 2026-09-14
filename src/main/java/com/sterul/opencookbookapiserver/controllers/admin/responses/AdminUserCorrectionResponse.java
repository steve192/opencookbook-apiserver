package com.sterul.opencookbookapiserver.controllers.admin.responses;

import com.sterul.opencookbookapiserver.services.nutrition.reports.UserCorrectionReport;

/**
 * @param food        null when excluded
 * @param matcherFood null when the matcher suggests nothing
 */
public record AdminUserCorrectionResponse(String name, String language, AdminFoodReference food, long userCount,
        AdminFoodReference matcherFood, Double matcherConfidence) {

    public static AdminUserCorrectionResponse of(UserCorrectionReport.UserCorrection correction) {
        return new AdminUserCorrectionResponse(correction.name(), correction.language(), AdminFoodReference.of(correction.food()),
                correction.userCount(), AdminFoodReference.of(correction.matcherFood()), correction.matcherConfidence());
    }
}
