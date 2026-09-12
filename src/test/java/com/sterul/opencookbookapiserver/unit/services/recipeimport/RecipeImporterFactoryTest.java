package com.sterul.opencookbookapiserver.unit.services.recipeimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.recipeimport.ChefkochImporter;
import com.sterul.opencookbookapiserver.services.recipeimport.RecipeImporterFactory;
import com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers.RecipeScrapersWebserviceImporter;

class RecipeImporterFactoryTest {

    private final ChefkochImporter chefkoch = mock(ChefkochImporter.class);
    private final RecipeScrapersWebserviceImporter scrapers =
            mock(RecipeScrapersWebserviceImporter.class);
    private final OpencookbookConfiguration configuration = mock(OpencookbookConfiguration.class);

    private RecipeImporterFactory cut;

    @BeforeEach
    void setup() {
        cut = new RecipeImporterFactory(chefkoch, scrapers, configuration);
    }

    /**
     * Somebody pasting something that is not a link is making an ordinary mistake, and the app
     * has to be able to say so. Splitting the string on slashes and indexing into the pieces
     * threw ArrayIndexOutOfBounds, which reached the caller as a server error.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "not a url at all",
        "https:missing-the-slashes",
        "https://",
        "http:// spaces are not allowed/recipe",
        "://nohost/recipe",
    })
    void somethingThatIsNotALinkIsTheCallersMistake(String notAUrl) {
        var thrown = assertThrows(ApiException.class, () -> cut.getRecipeImporter(notAUrl));

        assertEquals(ApiErrorCode.IMPORT_URL_INVALID, thrown.getErrorCode());
    }

    @Test
    void aHostWithNoDotsIsNotACrash() throws ApiException {
        when(configuration.getRecipeScaperServiceUrl()).thenReturn("");

        assertThrows(ApiException.class, () -> cut.getRecipeImporter("https://localhost/recipe"));
    }

    @Test
    void theScraperServiceTakesEverythingWhenItIsConfigured() throws ApiException {
        when(configuration.getRecipeScaperServiceUrl()).thenReturn("http://recipe-scrapers:9090");

        assertEquals(scrapers, cut.getRecipeImporter("https://www.example.com/some/recipe"));
    }

    @Test
    void chefkochIsRecognisedThroughItsSubdomains() throws ApiException {
        when(configuration.getRecipeScaperServiceUrl()).thenReturn("");

        assertEquals(chefkoch, cut.getRecipeImporter("https://www.chefkoch.de/rezepte/123/x.html"));
    }
}
