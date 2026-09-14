package com.sterul.opencookbookapiserver.configurations.nutrition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Registers a bean only on an instance that has nutrition estimation switched on. */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(prefix = "opencookbook.nutrition", name = "enabled", havingValue = "true")
public @interface ConditionalOnNutritionEnabled {
}
