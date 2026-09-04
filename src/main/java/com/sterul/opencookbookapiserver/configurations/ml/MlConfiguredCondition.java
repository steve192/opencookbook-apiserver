package com.sterul.opencookbookapiserver.configurations.ml;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Matches only when this instance actually has a machine learning subsystem to talk to. */
public class MlConfiguredCondition implements Condition {

    static final String SERVICE_URL_PROPERTY = "opencookbook.ml.service-url";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var serviceUrl = Binder.get(context.getEnvironment())
                .bind(SERVICE_URL_PROPERTY, String.class)
                .orElse("");
        return !serviceUrl.isBlank();
    }
}
