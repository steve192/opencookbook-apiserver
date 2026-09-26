package com.sterul.opencookbookapiserver.controllers.households;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.households.ConditionalOnHouseholdsEnabled;
import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.households.requests.HouseholdRequest;
import com.sterul.opencookbookapiserver.controllers.households.requests.SharingRequest;
import com.sterul.opencookbookapiserver.controllers.households.responses.HouseholdInviteResponse;
import com.sterul.opencookbookapiserver.controllers.households.responses.HouseholdResponse;
import com.sterul.opencookbookapiserver.controllers.support.HouseholdResponses;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.households.HouseholdInviteLinkFactory;
import com.sterul.opencookbookapiserver.services.households.HouseholdInviteService;
import com.sterul.opencookbookapiserver.services.households.HouseholdMembershipService;
import com.sterul.opencookbookapiserver.services.households.HouseholdService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@ConditionalOnHouseholdsEnabled
@RequestMapping(HouseholdPaths.BASE)
@Tag(name = "Households", description = "Sharing a cookbook and a weekplan with the people you cook with")
public class HouseholdController extends BaseController {

    private final HouseholdService householdService;
    private final HouseholdMembershipService memberships;
    private final HouseholdInviteService invites;
    private final HouseholdInviteLinkFactory linkFactory;
    private final HouseholdResponses householdResponses;

    public HouseholdController(HouseholdService householdService, HouseholdMembershipService memberships,
            HouseholdInviteService invites, HouseholdInviteLinkFactory linkFactory,
            HouseholdResponses householdResponses,
            SignedInUserService signedInUser) {
        super(signedInUser);
        this.householdService = householdService;
        this.memberships = memberships;
        this.invites = invites;
        this.linkFactory = linkFactory;
        this.householdResponses = householdResponses;
    }

    @Operation(summary = "The households you are in")
    @GetMapping
    public List<HouseholdResponse> getOwnHouseholds() {
        return householdResponses.summariesFor(getLoggedInUser());
    }

    @Operation(summary = "Start a household", description = "You become its first member.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdResponse create(@Valid @RequestBody HouseholdRequest request) {
        var household = householdService.create(request.name(), getLoggedInUser(), request.shareRecipes());
        return detailOf(household.getId());
    }

    @Operation(summary = "One household you are in")
    @GetMapping("/{householdId}")
    public HouseholdResponse getSingle(@Valid @NotBlank @PathVariable String householdId) {
        return detailOf(householdId);
    }

    @Operation(summary = "Rename a household", description = "Any member may; there are no roles.")
    @PutMapping("/{householdId}")
    public HouseholdResponse rename(@Valid @NotBlank @PathVariable String householdId,
            @Valid @RequestBody HouseholdRequest request) {
        householdService.rename(householdId, request.name(), getLoggedInUser());
        return detailOf(householdId);
    }

    @Operation(summary = "Share your cookbook with this household, or stop",
            description = "Always about your own cookbook. Stopping takes your recipes out of the "
                    + "household at once and removes your meals from its weekplan.")
    @PutMapping("/{householdId}/sharing")
    public HouseholdResponse setSharing(@Valid @NotBlank @PathVariable String householdId,
            @Valid @RequestBody SharingRequest request) {
        memberships.setSharing(householdId, getLoggedInUser(), request.shareRecipes());
        return detailOf(householdId);
    }

    @Operation(summary = "Leave, or remove another member",
            description = "Every member may remove every member; a household owns nothing, so "
                    + "whoever goes takes their own cookbook and nothing else.")
    @DeleteMapping("/{householdId}/members/{memberUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@Valid @NotBlank @PathVariable String householdId,
            @PathVariable Long memberUserId) {
        householdService.removeMember(householdId, memberUserId, getLoggedInUser());
    }

    @Operation(summary = "Create an invite link", description = "Lets one person join, until it lapses or is revoked.")
    @PostMapping("/{householdId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdInviteResponse createInvite(@Valid @NotBlank @PathVariable String householdId) {
        var invite = invites.create(householdId, getLoggedInUser());
        return HouseholdInviteResponse.of(invite, linkFactory.linkTo(invite.getId()));
    }

    @Operation(summary = "The invite links of this household that are still valid")
    @GetMapping("/{householdId}/invites")
    public List<HouseholdInviteResponse> getInvites(@Valid @NotBlank @PathVariable String householdId) {
        return invites.liveInvitesOf(householdId, getLoggedInUser()).stream()
                .map(invite -> HouseholdInviteResponse.of(invite, linkFactory.linkTo(invite.getId())))
                .toList();
    }

    @Operation(summary = "Revoke an invite link")
    @DeleteMapping("/{householdId}/invites/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvite(@Valid @NotBlank @PathVariable String householdId,
            @Valid @NotBlank @PathVariable String inviteId) {
        invites.revoke(householdId, inviteId, getLoggedInUser());
    }

    private HouseholdResponse detailOf(String householdId) {
        return householdResponses.detailFor(householdId, getLoggedInUser());
    }
}
