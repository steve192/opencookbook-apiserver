package com.sterul.opencookbookapiserver.configurations.shopping;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Registers a bean only on an instance that tells open apps about list changes at once. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "opencookbook.shopping", name = "live-enabled",
        havingValue = "true", matchIfMissing = true)
public @interface ConditionalOnShoppingLive {
}
