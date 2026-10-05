package com.sterul.opencookbookapiserver.unit.configurations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.type.AnnotationMetadata;

import com.sterul.opencookbookapiserver.configurations.PropertyNotBlankCondition;
import com.sterul.opencookbookapiserver.configurations.google.ConditionalOnGoogleSignIn;
import com.sterul.opencookbookapiserver.configurations.ml.ConditionalOnMlConfigured;

/**
 * Whether a feature is on, decided from configuration alone.
 *
 * The same setting reaches the application under three spellings - the camel case key in
 * application.yml, the kebab case one a test or a command line uses, and the shouty environment
 * variable the compose file passes - and getting any of them wrong would silently switch the
 * feature off on a properly configured instance, or on for everyone else.
 */
class PropertyNotBlankConditionTest {

    private final PropertyNotBlankCondition cut = new PropertyNotBlankCondition();

    @ConditionalOnMlConfigured
    static class NeedsMl {
    }

    @ConditionalOnGoogleSignIn
    static class NeedsGoogle {
    }

    @ConditionalOnMlConfigured
    @ConditionalOnGoogleSignIn
    static class NeedsBoth {
    }

    @Test
    void anInstanceWithNoSubsystemDoesNotMatch() {
        assertFalse(matches(Map.of()));
    }

    @Test
    void theEmptyDefaultInThePublishedComposeFileDoesNotCount() {
        // The published .env sets ML_SERVICE_URL= for everyone. Present but blank has to mean
        // "no subsystem", or the feature switches itself on for every self-hoster.
        assertFalse(matches(Map.of("opencookbook.ml.serviceUrl", "")));
        assertFalse(matches(Map.of("opencookbook.ml.service-url", "   ")));
    }

    @Test
    void theCamelCaseKeyFromApplicationYmlIsFound() {
        assertTrue(matches(Map.of("opencookbook.ml.serviceUrl", "https://ml.example.com")));
    }

    @Test
    void theKebabCaseKeyFromATestOrCommandLineIsFound() {
        assertTrue(matches(Map.of("opencookbook.ml.service-url", "https://ml.example.com")));
    }

    @Test
    void theEnvironmentVariableTheComposeFilePassesIsFound() {
        assertTrue(matchesEnvironment(
                Map.of("OPENCOOKBOOK_ML_SERVICEURL", "https://ml.example.com")));
    }

    @Test
    void anEmptyEnvironmentVariableStillMeansNoSubsystem() {
        assertFalse(matchesEnvironment(Map.of("OPENCOOKBOOK_ML_SERVICEURL", "")));
    }

    @Test
    void eachFeatureReadsItsOwnProperty() {
        var environment = environmentWith(new SystemEnvironmentPropertySource("systemEnvironment",
                Map.of("OPENCOOKBOOK_AUTH_GOOGLE_CLIENTID", "client.apps.googleusercontent.com")));

        assertTrue(evaluate(environment, NeedsGoogle.class));
        assertFalse(evaluate(environment, NeedsMl.class));
        assertFalse(evaluate(environment, NeedsBoth.class));
    }

    private boolean matches(Map<String, Object> properties) {
        return evaluate(environmentWith(new MapPropertySource("test", properties)), NeedsMl.class);
    }

    private boolean matchesEnvironment(Map<String, Object> variables) {
        return evaluate(environmentWith(new SystemEnvironmentPropertySource("systemEnvironment", variables)),
                NeedsMl.class);
    }

    private static StandardEnvironment environmentWith(MapPropertySource source) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(source);
        return environment;
    }

    private boolean evaluate(StandardEnvironment environment, Class<?> annotated) {
        var context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return cut.matches(context, AnnotationMetadata.introspect(annotated));
    }
}
