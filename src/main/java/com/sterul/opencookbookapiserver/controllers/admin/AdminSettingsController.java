package com.sterul.opencookbookapiserver.controllers.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.admin.requests.AdminSettingsRequest;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminSettingsResponse;
import com.sterul.opencookbookapiserver.services.instance.InstanceSettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(AdminPaths.BASE + "/settings")
@Tag(name = "Settings", description = "What an administrator chooses for the whole instance")
public class AdminSettingsController {

    private final InstanceSettingsService settings;

    public AdminSettingsController(InstanceSettingsService settings) {
        this.settings = settings;
    }

    @Operation(summary = "The instance's settings")
    @GetMapping
    public AdminSettingsResponse getSettings() {
        return new AdminSettingsResponse(settings.getSignupMode());
    }

    @Operation(summary = "Change the instance's settings")
    @PutMapping
    public AdminSettingsResponse updateSettings(@Valid @RequestBody AdminSettingsRequest request) {
        return new AdminSettingsResponse(settings.setSignupMode(request.signupMode()));
    }
}
