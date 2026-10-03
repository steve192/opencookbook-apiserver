package com.sterul.opencookbookapiserver.controllers.admin.responses;

import java.time.Instant;

import com.sterul.opencookbookapiserver.entities.account.Invitation;
import com.sterul.opencookbookapiserver.services.AccountLinkFactory;

/** @param createdBy the address of the administrator who created it, null once they are deleted */
public record AdminInvitationResponse(String id, String link, String createdBy, Instant createdOn, Instant expiresAt) {

    public static AdminInvitationResponse of(Invitation invitation, AccountLinkFactory links) {
        var creator = invitation.getCreatedBy();
        return new AdminInvitationResponse(
                invitation.getId(),
                links.invitation(invitation),
                creator == null ? null : creator.getEmailAddress(),
                invitation.getCreatedOn(),
                invitation.getExpiresAt());
    }
}
