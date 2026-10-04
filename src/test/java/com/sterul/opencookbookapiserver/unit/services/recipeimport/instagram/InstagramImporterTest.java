package com.sterul.opencookbookapiserver.unit.services.recipeimport.instagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.RecipeImageService;
import com.sterul.opencookbookapiserver.services.recipeimport.instagram.InstagramImporter;
import com.sterul.opencookbookapiserver.services.recipeimport.instagram.InstagramReadRateLimiter;
import com.sterul.opencookbookapiserver.services.recipeimport.text.RecipeTextImporter;
import com.sterul.opencookbookapiserver.services.recipeimport.text.RecipeTextReader;
import com.sterul.opencookbookapiserver.unit.MovableClock;
import com.sterul.opencookbookapiserver.unit.services.catalogue.ShippedCatalogueDataset;
import com.sterul.opencookbookapiserver.unit.services.recipeimport.StubbedHttp;

class InstagramImporterTest {

    private static final String POST = "https://www.instagram.com/p/AbC_12-x/";
    private static final String PHOTO = "https://scontent.cdninstagram.com/v/photo.jpg";
    private static final String RECIPE_CAPTION = "Pancakes\n\nZutaten:\n200 g Mehl\n2 Eier\n\nAlles verrühren und ausbacken.";

    private final RecipeImageService recipeImageService = mock(RecipeImageService.class);
    private MovableClock clock;
    private StubbedHttp http;
    private InstagramImporter cut;
    private CookpalUser owner;

    @BeforeEach
    void setup() throws IOException {
        clock = new MovableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var configuration = new OpencookbookConfiguration();
        configuration.setMaxImageSize(1_000_000L);
        configuration.getRecipeImport().setInstagramReadsPerHourPerUser(2);
        var textImporter = new RecipeTextImporter(new RecipeTextReader(ShippedCatalogueDataset.UNIT_LEXICON),
                ShippedCatalogueDataset.ingredientExtractor());
        cut = new InstagramImporter(recipeImageService, configuration, textImporter,
                new InstagramReadRateLimiter(configuration, clock));
        http = new StubbedHttp().page(POST, page(RECIPE_CAPTION, PHOTO)).image(PHOTO, new byte[] { 1, 2, 3 });
        ReflectionTestUtils.setField(cut, "client", http.client());
        owner = new CookpalUser();
        owner.setUserId(7L);
        when(recipeImageService.saveNewImage(any(InputStream.class), anyLong(), any(CookpalUser.class)))
                .thenReturn(RecipeImage.builder().uuid("photo-uuid").build());
    }

    @Test
    void theCaptionBecomesADraftWithThePhoto() {
        var draft = cut.importRecipe(POST, owner);

        assertEquals("Pancakes", draft.getTitle());
        assertEquals(List.of("Mehl", "Eier"),
                draft.getNeededIngredients().stream().map(need -> need.getIngredient().getName()).toList());
        assertEquals(List.of("Alles verrühren und ausbacken."), draft.getPreparationSteps());
        assertEquals("photo-uuid", draft.getImages().get(0).getUuid());
        assertEquals(POST, draft.getRecipeSource());
        assertEquals(owner, draft.getOwner());
        assertTrue(cut.needsReview());
    }

    @Test
    void theSharedLinkIsRebuiltAndReadLikeACrawler() throws Exception {
        cut.importRecipe("https://instagram.com/reel/AbC_12-x/?igsh=MWQ1ZGUx", owner);

        var request = http.requests().get(0);
        assertEquals(POST, request.getUri().toString());
        assertEquals("facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)",
                request.getFirstHeader(HttpHeaders.USER_AGENT).getValue());
        assertFalse(request.getConfig().isRedirectsEnabled());
        assertEquals(10, request.getConfig().getResponseTimeout().toSeconds());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://www.instagram.com/p/AbC_12-x",
        "https://www.instagram.com/reels/AbC_12-x/",
        "https://www.instagram.com/tv/AbC_12-x/",
        "https://m.instagram.com/p/AbC_12-x/",
        "http://instagr.am/p/AbC_12-x/",
        "https://www.instagram.com/test.kitchen/p/AbC_12-x/",
    })
    void everyShapeOfAPostLinkIsReadFromTheSameAddress(String link) throws Exception {
        cut.importRecipe(link, owner);

        assertEquals(POST, http.requests().get(0).getUri().toString());
    }

    @Test
    void aLinkToSomethingElseThanAPostIsNotSupported() {
        var thrown = assertThrows(ApiException.class,
                () -> cut.importRecipe("https://www.instagram.com/testkitchen/", owner));

        assertEquals(ApiErrorCode.IMPORT_NOT_SUPPORTED, thrown.getErrorCode());
        assertTrue(http.requests().isEmpty());
    }

    @Test
    void aPostThatRedirectsToTheSignInIsUnavailable() {
        http.status(POST, 302);

        var thrown = assertThrows(ApiException.class, () -> cut.importRecipe(POST, owner));

        assertEquals(ApiErrorCode.IMPORT_SOURCE_UNAVAILABLE, thrown.getErrorCode());
    }

    @Test
    void aPageWithoutACaptionIsUnavailable() {
        http.page(POST, "<html><head><title>Instagram</title></head></html>");

        var thrown = assertThrows(ApiException.class, () -> cut.importRecipe(POST, owner));

        assertEquals(ApiErrorCode.IMPORT_SOURCE_UNAVAILABLE, thrown.getErrorCode());
    }

    @Test
    void aCaptionPointingElsewhereIsNoRecipeAndCarriesTheLink() {
        http.page(POST, page("Neues Rezept auf dem Blog: https://example.com/kuchen", PHOTO));

        var thrown = assertThrows(ApiException.class, () -> cut.importRecipe(POST, owner));

        assertEquals(ApiErrorCode.IMPORT_NO_RECIPE, thrown.getErrorCode());
        assertEquals("https://example.com/kuchen", thrown.getLink());
    }

    @Test
    void aPhotoThatCannotBeFetchedLeavesTheDraftWithoutOne() throws IOException {
        http.status(PHOTO, 404);
        when(recipeImageService.saveNewImage(any(InputStream.class), anyLong(), any(CookpalUser.class)))
                .thenThrow(new IOException("not an image"));

        var draft = cut.importRecipe(POST, owner);

        assertTrue(draft.getImages().isEmpty());
    }

    @Test
    void readsBeyondTheHourlyBudgetAreRefusedWithoutAskingInstagram() {
        cut.importRecipe(POST, owner);
        cut.importRecipe(POST, owner);
        var asked = http.requests().size();

        var thrown = assertThrows(ApiException.class, () -> cut.importRecipe(POST, owner));

        assertEquals(ApiErrorCode.RATE_LIMITED, thrown.getErrorCode());
        assertEquals(asked, http.requests().size());
    }

    @Test
    void theBudgetIsPerUserAndComesBackAfterAnHour() {
        cut.importRecipe(POST, owner);
        cut.importRecipe(POST, owner);
        var somebodyElse = new CookpalUser();
        somebodyElse.setUserId(8L);

        assertEquals("Pancakes", cut.importRecipe(POST, somebodyElse).getTitle());
        clock.advanceBy(Duration.ofHours(1));
        assertEquals("Pancakes", cut.importRecipe(POST, owner).getTitle());
    }

    @Test
    void itReportsItsHost() {
        assertEquals(List.of("instagram.com"), cut.getSupportedHostnames());
    }

    private static String page(String caption, String image) {
        return """
                <html><head>
                <meta property="og:image" content="%s" />
                <meta property="og:description" content="12 likes, 3 comments - testkitchen on May 2, 2026: &quot;%s&quot;" />
                </head></html>
                """.formatted(image, caption);
    }
}
