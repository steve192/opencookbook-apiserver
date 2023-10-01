package com.sterul.opencookbookapiserver.controllers;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

@RestController
public abstract class BaseController {

    @Autowired
    private UserRepository userRepository;

    protected CookpalUser getLoggedInUser() {
        var userEmailAddress = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAddress(userEmailAddress);
    }
}
