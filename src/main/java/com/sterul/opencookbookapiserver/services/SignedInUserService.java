package com.sterul.opencookbookapiserver.services;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/**
 * Resolves the account behind the current request.
 *
 * This used to be a repository reached from BaseController, which was the one place a
 * controller was allowed past the services layer. As a service it is an ordinary
 * collaborator, so that exception is gone and the lookup is injectable like anything else.
 */
@Service
public class SignedInUserService {

    private final UserRepository userRepository;

    public SignedInUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public CookpalUser get() {
        var userEmailAddress = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailAddress(userEmailAddress);
    }
}
