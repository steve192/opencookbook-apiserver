package com.sterul.opencookbookapiserver.controllers;

import com.sterul.opencookbookapiserver.controllers.responses.InstanceInfoResponse;
import com.sterul.opencookbookapiserver.services.InstanceInfoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instance")
@Tag(name = "Instance Info", description = "Info about the server instance")
@Slf4j
public class InstanceInfoController extends BaseController {

    @Autowired
    private InstanceInfoService instanceInfoService;

    @GetMapping("")
    public InstanceInfoResponse getInstanceInfo() {
        return InstanceInfoResponse.builder()
                .termsOfService(instanceInfoService.getTermsOfSerivice())
                .build();
    }
}
