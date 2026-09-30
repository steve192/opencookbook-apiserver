package com.sterul.opencookbookapiserver.configurations.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.sterul.opencookbookapiserver.services.AccessTokenService;

/** The id of the sign in the request's access token was issued for. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "getClaimAsString('" + AccessTokenService.SESSION_CLAIM + "')")
public @interface CurrentSignIn {
}
