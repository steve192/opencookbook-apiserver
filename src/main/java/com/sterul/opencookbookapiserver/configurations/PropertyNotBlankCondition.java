package com.sterul.opencookbookapiserver.configurations;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Matches when every property of {@link ConditionalOnPropertyNotBlank} on the bean holds more than blanks. */
public class PropertyNotBlankCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var binder = Binder.get(context.getEnvironment());
        return metadata.getAnnotations().stream(ConditionalOnPropertyNotBlank.class)
                .map(annotation -> annotation.getString(MergedAnnotation.VALUE))
                .allMatch(property -> !binder.bind(property, String.class).orElse("").isBlank());
    }
}
