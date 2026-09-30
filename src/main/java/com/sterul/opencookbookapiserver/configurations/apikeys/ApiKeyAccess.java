package com.sterul.opencookbookapiserver.configurations.apikeys;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.sterul.opencookbookapiserver.entities.account.ApiScope;

/**
 * Opens an endpoint to api keys. Without it a key is refused, whatever its scopes. Read into the api
 * key filter chain by ApiKeyEndpoints; password logins are not affected.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiKeyAccess {

    /** The key needs one of these; empty lets in any key. */
    ApiScope[] value() default {};
}
