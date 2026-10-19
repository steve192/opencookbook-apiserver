package com.sterul.opencookbookapiserver.services;

import java.util.Optional;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
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

    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    private final UserRepository userRepository;

    public SignedInUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** The account behind the request; a token whose account is gone has to sign in again. */
    public CookpalUser get() {
        return find().orElseThrow(() -> new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED,
                "No account behind the request"));
    }

    /** Empty for a request nobody signed in to, which only reaches public endpoints. */
    public Optional<CookpalUser> find() {
        return signedInName().map(userRepository::findByEmailAddress);
    }

    public boolean isSignedIn() {
        return signedInName().isPresent();
    }

    /** The account's name, without reading the account; empty for a request nobody signed in to. */
    public Optional<String> signedInName() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && TRUST_RESOLVER.isAuthenticated(authentication)
                ? Optional.of(authentication.getName())
                : Optional.empty();
    }
}
