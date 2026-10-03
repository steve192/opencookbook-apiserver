package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.admin.requests.AdminInvitationRequest;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminInvitationResponse;
import com.sterul.opencookbookapiserver.services.AccountLinkFactory;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.invitations.InvitationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping(AdminPaths.BASE + "/invitations")
@Tag(name = "Invitations", description = "Single use links that create an active account")
public class AdminInvitationController extends BaseController {

    private final InvitationService invitations;
    private final AccountLinkFactory links;

    public AdminInvitationController(InvitationService invitations, AccountLinkFactory links,
            SignedInUserService signedInUser) {
        super(signedInUser);
        this.invitations = invitations;
        this.links = links;
    }

    @Operation(summary = "Every invitation that has not expired or been used")
    @GetMapping
    public List<AdminInvitationResponse> getOpenInvitations() {
        return invitations.getOpenInvitations().stream()
                .map(invitation -> AdminInvitationResponse.of(invitation, links))
                .toList();
    }

    @Operation(summary = "Create an invitation link",
            description = "Whoever opens it picks their own address and password. With sendTo the link is "
                    + "mailed there as well, which needs SMTP, MAIL_FROM and INSTANCE_URL to be configured.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminInvitationResponse createInvitation(@Valid @RequestBody AdminInvitationRequest request) {
        var invitation = invitations.create(getLoggedInUser(), request.validForDays(), request.sendTo());
        return AdminInvitationResponse.of(invitation, links);
    }

    @Operation(summary = "Revoke an invitation", description = "The link stops working immediately.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeInvitation(@PathVariable String id) {
        invitations.revoke(id);
    }
}
