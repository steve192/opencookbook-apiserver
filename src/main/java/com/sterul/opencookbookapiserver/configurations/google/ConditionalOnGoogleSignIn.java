package com.sterul.opencookbookapiserver.configurations.google;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.sterul.opencookbookapiserver.configurations.ConditionalOnPropertyNotBlank;

/** Registers a bean only on an instance that offers signing in with Google. */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnPropertyNotBlank("opencookbook.auth.google.client-id")
public @interface ConditionalOnGoogleSignIn {
}
