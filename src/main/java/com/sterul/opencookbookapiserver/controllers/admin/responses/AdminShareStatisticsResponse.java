package com.sterul.opencookbookapiserver.controllers.admin.responses;

public record AdminShareStatisticsResponse(long totalShares, long totalAccesses, long expiringSoon) {
}
