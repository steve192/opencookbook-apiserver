package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminInstanceCheckResponse;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminInstanceResponse;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.instance.InstanceChecks;
import com.sterul.opencookbookapiserver.services.instance.InstanceOverviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(AdminPaths.BASE + "/instance")
@Tag(name = "Instance", description = "How this instance is configured and whether its services answer")
public class AdminInstanceController extends BaseController {

    private final InstanceOverviewService overviewService;
    private final InstanceChecks checks;

    public AdminInstanceController(InstanceOverviewService overviewService, InstanceChecks checks,
            SignedInUserService signedInUser) {
        super(signedInUser);
        this.overviewService = overviewService;
        this.checks = checks;
    }

    @Operation(summary = "What the environment configured, and which features that turns on",
            description = "Carries no secret: no SMTP credentials, no machine learning token, nothing about the JWT.")
    @GetMapping
    public AdminInstanceResponse getOverview() {
        return AdminInstanceResponse.of(overviewService.overview());
    }

    @Operation(summary = "Ask the mail server, the recipe import and the recipe scan service whether they answer",
            description = "All at once, each for a few seconds at most.")
    @GetMapping("/checks")
    public List<AdminInstanceCheckResponse> runChecks() {
        return checks.runAll().stream().map(AdminInstanceCheckResponse::of).toList();
    }

    @Operation(summary = "Send a test mail to the signed in administrator",
            description = "A failure answers MAIL_DELIVERY_FAILED with the mail server's reason as the message.")
    @PostMapping("/test-mail")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendTestMail() {
        checks.sendTestMail(getLoggedInUser());
    }
}
