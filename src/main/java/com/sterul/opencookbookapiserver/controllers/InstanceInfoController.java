package com.sterul.opencookbookapiserver.controllers;

import java.util.Optional;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.controllers.responses.InstanceInfoResponse;
import com.sterul.opencookbookapiserver.services.InstanceInfoService;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.ml.MlAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instance")
@Tag(name = "Instance Info", description = "Info about the server instance")
@Slf4j
public class InstanceInfoController extends BaseController {

    private final InstanceInfoService instanceInfoService;
    private final OpencookbookConfiguration opencookbookConfiguration;

    /**
     * Absent on an instance with no machine learning subsystem, which is the common case.
     */
    private final Optional<MlAvailabilityService> mlAvailabilityService;

    public InstanceInfoController(InstanceInfoService instanceInfoService,
            OpencookbookConfiguration opencookbookConfiguration,
            Optional<MlAvailabilityService> mlAvailabilityService,
            SignedInUserService signedInUser) {
        super(signedInUser);
        this.instanceInfoService = instanceInfoService;
        this.opencookbookConfiguration = opencookbookConfiguration;
        this.mlAvailabilityService = mlAvailabilityService;
    }

    @Operation(summary = "What this instance offers",
            description = "Read before signing in, so the app only offers what the server can do.")
    @GetMapping("")
    public InstanceInfoResponse getInstanceInfo() {
        return InstanceInfoResponse.builder()
                .termsOfService(instanceInfoService.getTermsOfSerivice())
                .sharingEnabled(opencookbookConfiguration.getSharing().isEnabled())
                .householdsEnabled(opencookbookConfiguration.getHouseholds().isEnabled())
                .nutritionEnabled(opencookbookConfiguration.getNutrition().isEnabled())
                .ocrImportEnabled(isOcrImportEnabled())
                .build();
    }

    private boolean isOcrImportEnabled() {
        return mlAvailabilityService.map(MlAvailabilityService::isRecipeOcrAvailable).orElse(false);
    }
}
