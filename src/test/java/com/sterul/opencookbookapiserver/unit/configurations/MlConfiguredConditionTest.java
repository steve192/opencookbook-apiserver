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
import org.springframework.core.type.AnnotatedTypeMetadata;

import com.sterul.opencookbookapiserver.configurations.ml.MlConfiguredCondition;

/**
 * Whether this instance has a subsystem, decided from configuration alone.
 *
 * The same setting reaches the application under three spellings - the camel case key in
 * application.yml, the kebab case one a test or a command line uses, and the shouty environment
 * variable the compose file passes - and getting any of them wrong would silently switch the
 * feature off on a properly configured instance, or on for everyone else.
 */
class MlConfiguredConditionTest {

    private final MlConfiguredCondition cut = new MlConfiguredCondition();

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

    private boolean matches(Map<String, Object> properties) {
        var environment = new StandardEnvironment();
        environment.getPropertySources()
                .addFirst(new MapPropertySource("test", properties));
        return evaluate(environment);
    }

    private boolean matchesEnvironment(Map<String, Object> variables) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("systemEnvironment", variables));
        return evaluate(environment);
    }

    private boolean evaluate(StandardEnvironment environment) {
        var context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return cut.matches(context, mock(AnnotatedTypeMetadata.class));
    }
}
