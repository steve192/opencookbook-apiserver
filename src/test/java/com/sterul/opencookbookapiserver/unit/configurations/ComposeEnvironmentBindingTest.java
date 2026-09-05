package com.sterul.opencookbookapiserver.unit.configurations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;

/**
 * The machine learning names the compose file passes in.
 *
 * These reach the application as environment variables and are bound by spring's own relaxed
 * naming rather than by a placeholder in application.yml, so nothing in this repository
 * mentions them by name. A misspelling would therefore not fail: the setting would silently
 * keep its default, and an operator would be left wondering why the value they set does
 * nothing. This pins the exact spellings in docker-compose.yml.
 */
class ComposeEnvironmentBindingTest {

    @Test
    void everyNameTheComposeFilePassesReachesItsSetting() {
        var ml = bind(Map.of(
                "OPENCOOKBOOK_ML_SERVICEURL", "https://ml.example.com",
                "OPENCOOKBOOK_ML_APITOKEN", "cpml_a_token",
                "OPENCOOKBOOK_ML_SUBMITTERSALT", "a-salt",
                "OPENCOOKBOOK_ML_REQUESTTIMEOUTSECONDS", "45",
                "OPENCOOKBOOK_ML_JOBTIMEOUTSECONDS", "600",
                "OPENCOOKBOOK_ML_JOBRETENTIONHOURS", "48",
                "OPENCOOKBOOK_ML_RECIPEOCR_ENABLED", "false",
                "OPENCOOKBOOK_ML_RECIPEOCR_JOBSPERUSERPERDAY", "5",
                "OPENCOOKBOOK_ML_RECIPEOCR_MAXPAGES", "3"));

        assertEquals("https://ml.example.com", ml.getServiceUrl());
        assertEquals("cpml_a_token", ml.getApiToken());
        assertEquals("a-salt", ml.effectiveSubmitterSalt());
        assertEquals(45, ml.getRequestTimeoutSeconds());
        assertEquals(600, ml.getJobTimeoutSeconds());
        assertEquals(48, ml.getJobRetentionHours());
        assertEquals(false, ml.getRecipeOcr().isEnabled());
        assertEquals(5, ml.getRecipeOcr().getJobsPerUserPerDay());
        assertEquals(3, ml.getRecipeOcr().getMaxPages());
    }

    @Test
    void anInstallationThatSetsNoneOfThemKeepsTheDefaults() {
        var ml = bind(Map.of());

        assertTrue(ml.getServiceUrl().isEmpty());
        assertTrue(ml.getRecipeOcr().isEnabled());
        assertEquals(20, ml.getRecipeOcr().getJobsPerUserPerDay());
        assertEquals(6, ml.getRecipeOcr().getMaxPages());
    }

    private OpencookbookConfiguration.Ml bind(Map<String, Object> variables) {
        return Binder.get(environmentWith(variables))
                .bindOrCreate("opencookbook", OpencookbookConfiguration.class)
                .getMl();
    }

    private StandardEnvironment environmentWith(Map<String, Object> variables) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("systemEnvironment", variables));
        return environment;
    }
}
