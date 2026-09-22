package com.sterul.opencookbookapiserver.controllers.households;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.households.ConditionalOnHouseholdsEnabled;
import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.households.requests.AcceptInviteRequest;
import com.sterul.opencookbookapiserver.controllers.households.responses.HouseholdResponse;
import com.sterul.opencookbookapiserver.controllers.households.responses.InvitePreviewResponse;
import com.sterul.opencookbookapiserver.controllers.support.HouseholdResponses;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.households.HouseholdInviteService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Authenticated, unlike a share link: joining needs an account anyway. */
@RestController
@ConditionalOnHouseholdsEnabled
@RequestMapping(HouseholdPaths.INVITE_BASE)
@Tag(name = "Household invites", description = "Joining a household you were invited to")
public class HouseholdInviteController extends BaseController {

    private final HouseholdInviteService invites;
    private final HouseholdResponses householdResponses;

    public HouseholdInviteController(HouseholdInviteService invites, HouseholdResponses householdResponses) {
        this.invites = invites;
        this.householdResponses = householdResponses;
    }

    @Operation(summary = "What an invite leads to", description = "The household's name, and nothing else.")
    @GetMapping("/{token}")
    public InvitePreviewResponse preview(@Valid @NotBlank @PathVariable String token) throws ApiException {
        return new InvitePreviewResponse(invites.preview(token).getName());
    }

    @Operation(summary = "Join the household this invite leads to")
    @PostMapping("/{token}/accept")
    public HouseholdResponse accept(@Valid @NotBlank @PathVariable String token,
            @Valid @RequestBody AcceptInviteRequest request) throws ApiException, ElementNotFound {
        var user = getLoggedInUser();
        var household = invites.accept(token, user, request.shareRecipes());
        return householdResponses.detailFor(household.getId(), user);
    }
}
