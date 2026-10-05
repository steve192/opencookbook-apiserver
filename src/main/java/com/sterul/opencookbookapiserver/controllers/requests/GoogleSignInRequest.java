package com.sterul.opencookbookapiserver.controllers.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param idToken    the ID token Google issued to one of this instance's clients
 * @param invitation the token of an invitation link, used only when the account does not exist yet
 */
public record GoogleSignInRequest(
        @NotBlank @Size(max = 4096) String idToken,
        @Size(max = 64) String invitation) {
}
