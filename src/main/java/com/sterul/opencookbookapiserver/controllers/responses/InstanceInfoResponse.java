package com.sterul.opencookbookapiserver.controllers.responses;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InstanceInfoResponse {
    private String termsOfService;
}
