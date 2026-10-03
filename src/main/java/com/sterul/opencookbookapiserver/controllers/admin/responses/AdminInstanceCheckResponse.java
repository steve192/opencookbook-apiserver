package com.sterul.opencookbookapiserver.controllers.admin.responses;

import com.sterul.opencookbookapiserver.services.instance.InstanceChecks;

public record AdminInstanceCheckResponse(InstanceChecks.Check check, InstanceChecks.Status status, String detail) {

    public static AdminInstanceCheckResponse of(InstanceChecks.Result result) {
        return new AdminInstanceCheckResponse(result.check(), result.status(), result.detail());
    }
}
