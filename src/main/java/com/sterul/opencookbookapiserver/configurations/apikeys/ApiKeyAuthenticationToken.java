package com.sterul.opencookbookapiserver.configurations.apikeys;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Transient;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.sterul.opencookbookapiserver.entities.account.ApiKey;
import com.sterul.opencookbookapiserver.entities.account.ApiScope;

/**
 * An api key, first as presented and then as checked. Its authorities are only its scopes, never the
 * owner's role, and its name is the owner's, so the signed-in user resolves as for a password login.
 */
@Transient
@SuppressWarnings("java:S2160") // The inherited equals compares principal and credentials through the getters.
public final class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final transient String secret;
    private final transient ApiKey key;

    private ApiKeyAuthenticationToken(String secret) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.secret = secret;
        this.key = null;
        setAuthenticated(false);
    }

    private ApiKeyAuthenticationToken(ApiKey key) {
        super(key.getScopes().stream().map(scope -> new SimpleGrantedAuthority(authorityOf(scope))).toList());
        this.secret = null;
        this.key = key;
        setAuthenticated(true);
    }

    public static ApiKeyAuthenticationToken unauthenticated(String secret) {
        return new ApiKeyAuthenticationToken(secret);
    }

    public static ApiKeyAuthenticationToken authenticated(ApiKey key) {
        return new ApiKeyAuthenticationToken(key);
    }

    /** Named as OAuth2 resource servers name scopes. */
    static String authorityOf(ApiScope scope) {
        return "SCOPE_" + scope.value();
    }

    @Override
    public String getCredentials() {
        return secret;
    }

    @Override
    public ApiKey getPrincipal() {
        return key;
    }

    @Override
    public String getName() {
        return key == null ? "" : key.getOwner().getEmailAddress();
    }
}
