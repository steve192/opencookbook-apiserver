package com.sterul.opencookbookapiserver.controllers.legal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.legal.OpenSourceComponentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Legal")
public class OpenSourceComponentController extends BaseController {

    private final OpenSourceComponentService components;

    public OpenSourceComponentController(OpenSourceComponentService components, SignedInUserService signedInUser) {
        super(signedInUser);
        this.components = components;
    }

    @Operation(summary = "What this server is built from, and the data its food catalogue comes from",
            description = "With the license and notices each asks to be passed on. The app adds its own components.")
    @GetMapping("/api/v1/open-source-components")
    public OpenSourceComponentsResponse getComponents() {
        return new OpenSourceComponentsResponse(List.of(
                new OpenSourceComponentsResponse.Section("server", components.serverComponents()),
                new OpenSourceComponentsResponse.Section("catalogue", components.catalogueSources())));
    }
}
