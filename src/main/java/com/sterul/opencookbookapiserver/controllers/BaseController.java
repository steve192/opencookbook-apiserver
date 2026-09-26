package com.sterul.opencookbookapiserver.controllers;

import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.services.SignedInUserService;

@RestController
public abstract class BaseController {

    private final SignedInUserService signedInUser;

    protected BaseController(SignedInUserService signedInUser) {
        this.signedInUser = signedInUser;
    }

    protected CookpalUser getLoggedInUser() {
        return signedInUser.get();
    }
}
