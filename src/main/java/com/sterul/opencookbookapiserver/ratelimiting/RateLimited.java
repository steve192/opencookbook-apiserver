package com.sterul.opencookbookapiserver.ratelimiting;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Puts an endpoint, or every endpoint of a controller, under the budget of its tier. On a method it replaces the
 * controller's.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {

    RateLimitTier value();

    /** For {@link RateLimitTier#SHARE_LINK}: the path variable holding the link, which gets a budget of its own. */
    String perPathVariable() default "";
}
