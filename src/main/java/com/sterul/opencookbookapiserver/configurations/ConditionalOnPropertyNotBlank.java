package com.sterul.opencookbookapiserver.configurations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

/**
 * Registers a bean only when a property is set to more than blanks. Unlike {@code @ConditionalOnProperty},
 * an empty value is off: the compose file passes every variable, set or not.
 */
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(PropertyNotBlankCondition.class)
public @interface ConditionalOnPropertyNotBlank {

    /** The property in its canonical (kebab case) form. */
    String value();
}
