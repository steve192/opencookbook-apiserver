package com.sterul.opencookbookapiserver.services.google;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.google.ConditionalOnGoogleSignIn;
import com.sterul.opencookbookapiserver.services.SignInService;
import com.sterul.opencookbookapiserver.services.SignInService.IssuedTokens;
import com.sterul.opencookbookapiserver.services.SignupService;
import com.sterul.opencookbookapiserver.services.UserService;
import com.sterul.opencookbookapiserver.services.exceptions.UserAlreadyExistsException;

/**
 * Signs in to the account of the address Google verified, creating it when the instance lets this person in.
 * An existing account opens only for an address Google hosts.
 */
@Service
@ConditionalOnGoogleSignIn
@Transactional
public class GoogleSignInService {

    private final GoogleIdTokens idTokens;
    private final UserService users;
    private final SignupService signups;
    private final SignInService signIns;

    public GoogleSignInService(GoogleIdTokens idTokens, UserService users, SignupService signups,
            SignInService signIns) {
        this.idTokens = idTokens;
        this.users = users;
        this.signups = signups;
        this.signIns = signIns;
    }

    /** @param invitation the token of an invitation link; only used when there is no account yet */
    public IssuedTokens signIn(String idToken, String invitation) {
        var address = idTokens.verifiedAddress(idToken);
        var user = users.getUserByEmail(address.emailAddress());
        if (user == null) {
            user = signups.signupVerified(address.emailAddress(), invitation);
        } else if (!address.hostedByGoogle()) {
            throw new UserAlreadyExistsException("Google does not vouch for the current owner of the address");
        } else {
            users.confirmAddress(user);
            users.rememberLanguageOfCurrentRequest(user);
        }
        users.linkGoogle(user);
        return signIns.signInWithoutPassword(user);
    }
}
