package com.sterul.opencookbookapiserver.configurations.ml;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

/** Registers a bean only on an instance that has a machine learning subsystem configured. */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(MlConfiguredCondition.class)
public @interface ConditionalOnMlConfigured {
}
