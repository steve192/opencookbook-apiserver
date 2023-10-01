package com.sterul.opencookbookapiserver.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.repositories.entities.account.User;

@RestController
public abstract class BaseController {

    @Autowired
    private UserRepository userRepository;

    protected User getLoggedInUser() {
        var userEmailAddress = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAddress(userEmailAddress);
    }
}
