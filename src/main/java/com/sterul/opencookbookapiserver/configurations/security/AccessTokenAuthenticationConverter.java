package com.sterul.opencookbookapiserver.configurations.security;

import java.util.ArrayList;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/**
 * The account behind a checked access token, read anew for every request: a deleted or deactivated
 * account is refused at once, and its role is the current one. When the sign in was made with the
 * password, that counts as a password factor from then on, which the admin api asks to be recent.
 */
@Component
public class AccessTokenAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    public AccessTokenAuthenticationConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var user = users.findById(Long.valueOf(jwt.getSubject()))
                .filter(CookpalUser::isActivated)
                .orElseThrow(() -> new InvalidBearerTokenException("The account is gone or deactivated"));

        var authorities = new ArrayList<>(AccountAuthorities.of(user));
        var passwordAt = jwt.getClaimAsInstant(IdTokenClaimNames.AUTH_TIME);
        if (passwordAt != null) {
            authorities.add(FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)
                    .issuedAt(passwordAt).build());
        }
        // Named by address, as a password login is, so the signed-in user resolves the same way.
        return new JwtAuthenticationToken(jwt, authorities, user.getEmailAddress());
    }
}
