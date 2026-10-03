package com.sterul.opencookbookapiserver.controllers.admin.requests;

import com.sterul.opencookbookapiserver.controllers.requests.EmailAddresses;
import com.sterul.opencookbookapiserver.services.invitations.InvitationService;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * @param validForDays 1, 7 or 30; left out means 7
 * @param sendTo       where to mail the link as well, or null (or blank) to only hand it over
 */
public record AdminInvitationRequest(
        Integer validForDays,
        @Email @Size(max = EmailAddresses.MAX_LENGTH) String sendTo) {

    public AdminInvitationRequest {
        if (validForDays == null) {
            validForDays = InvitationService.DEFAULT_VALIDITY_DAYS;
        }
        if (sendTo != null && sendTo.isBlank()) {
            sendTo = null;
        }
    }

    @AssertTrue(message = "must be 1, 7 or 30")
    @Schema(hidden = true)
    public boolean isValidityOffered() {
        return InvitationService.VALIDITY_DAYS.contains(validForDays);
    }
}
