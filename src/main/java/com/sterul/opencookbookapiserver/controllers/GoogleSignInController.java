package com.sterul.opencookbookapiserver.controllers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.google.ConditionalOnGoogleSignIn;
import com.sterul.opencookbookapiserver.controllers.requests.GoogleSignInRequest;
import com.sterul.opencookbookapiserver.controllers.responses.UserLoginResponse;
import com.sterul.opencookbookapiserver.services.google.GoogleSignInService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@ConditionalOnGoogleSignIn
@RequestMapping("/api/v1/users/login/google")
@Tag(name = "Users")
public class GoogleSignInController {

    private final GoogleSignInService googleSignIns;

    public GoogleSignInController(GoogleSignInService googleSignIns) {
        this.googleSignIns = googleSignIns;
    }

    @Operation(summary = "Signs in with a Google ID token",
            description = "Opens the account of the address Google verified, if Google hosts that address (Gmail, "
                    + "Workspace). Without an account, one is created when the instance is open or the invitation is "
                    + "valid. The admin api still asks for the password.")
    @PostMapping
    public UserLoginResponse signIn(@Valid @RequestBody GoogleSignInRequest request) {
        return UserLoginResponse.of(googleSignIns.signIn(request.idToken(), request.invitation()));
    }
}
