package com.sterul.opencookbookapiserver.controllers.setup;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.services.instance.SetupService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(SetupPaths.BASE)
@Tag(name = "Setup", description = "Creating the first administrator of a new instance")
public class SetupController {

    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @Operation(summary = "Create the first administrator",
            description = "Whoever comes first while no activated administrator exists. The account is "
                    + "active at once; signing in is a separate call.")
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setUp(@Valid @RequestBody SetupRequest request) {
        setupService.createFirstAdministrator(request.emailAddress(), request.password());
    }
}
