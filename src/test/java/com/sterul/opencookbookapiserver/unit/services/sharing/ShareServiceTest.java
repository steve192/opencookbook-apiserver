package com.sterul.opencookbookapiserver.unit.services.sharing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.sharing.Share;
import com.sterul.opencookbookapiserver.entities.sharing.ShareResourceType;
import com.sterul.opencookbookapiserver.entities.sharing.ShareVisibility;
import com.sterul.opencookbookapiserver.repositories.ShareRepository;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.sharing.ShareService;

/**
 * The rules a share follows, without a database or a clock that moves on its own.
 *
 * Two of these are about where a shared recipe comes from. A share holds a reference to its
 * recipe, so reading it straight off the entity would work - and would mean that anything
 * {@link RecipeService} does, or is later made to do, when handing out a recipe simply does not
 * happen for shared ones. These pin the recipe service as the only way in.
 */
@ExtendWith(MockitoExtension.class)
class ShareServiceTest {

    private static final String SHARE_ID = "share-id";
    private static final Long RECIPE_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    @Mock
    private ShareRepository shareRepository;
    @Mock
    private RecipeService recipeService;

    private final OpencookbookConfiguration configuration = new OpencookbookConfiguration();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private ShareService cut() {
        return new ShareService(shareRepository, recipeService, configuration, clock);
    }

    @Test
    void aSharedRecipeIsFetchedThroughTheRecipeServiceRatherThanOffTheShare() throws ElementNotFound {
        var recipeAsTheShareRefersToIt = recipeWithId(RECIPE_ID);
        var recipeAsTheRecipeServiceHandsItOut = recipeWithId(RECIPE_ID);
        when(shareRepository.findById(SHARE_ID))
                .thenReturn(Optional.of(liveShareOf(recipeAsTheShareRefersToIt)));
        when(recipeService.getRecipeById(RECIPE_ID)).thenReturn(recipeAsTheRecipeServiceHandsItOut);

        var resolved = cut().resolveSharedRecipe(SHARE_ID);

        assertSame(recipeAsTheRecipeServiceHandsItOut, resolved,
                "Navigating to the recipe through the share would skip whatever the recipe "
                        + "service does when it hands one out");
    }

    @Test
    void sharingARecipeTakesItUnderLock() throws ElementNotFound {
        when(recipeService.getRecipeForUpdate(RECIPE_ID)).thenReturn(recipeWithId(RECIPE_ID));
        when(shareRepository.findByRecipeAndVisibility(any(), any())).thenReturn(Optional.empty());
        when(shareRepository.save(any())).thenAnswer(saved -> saved.getArgument(0));

        cut().shareRecipePublicly(RECIPE_ID);

        // An unlocked read would let two requests arriving together both decide that the recipe
        // is not shared yet, and hand out two public links for it.
        verify(recipeService, times(1)).getRecipeForUpdate(RECIPE_ID);
        verify(recipeService, never()).getRecipeById(RECIPE_ID);
    }

    @Test
    void aShareBelongsToWhoeverOwnsTheRecipe() throws ElementNotFound {
        var recipeOwner = new CookpalUser();
        recipeOwner.setUserId(99L);
        var recipe = recipeWithId(RECIPE_ID);
        recipe.setOwner(recipeOwner);

        when(recipeService.getRecipeForUpdate(RECIPE_ID)).thenReturn(recipe);
        when(shareRepository.findByRecipeAndVisibility(any(), any())).thenReturn(Optional.empty());
        when(shareRepository.save(any())).thenAnswer(saved -> saved.getArgument(0));

        var share = cut().shareRecipePublicly(RECIPE_ID);

        // Read off the recipe rather than taken from the caller: a claim this service cannot
        // verify is one it should not accept.
        assertSame(recipeOwner, share.getOwner());
    }

    @Test
    void aNewShareLapsesAfterTheConfiguredNumberOfDays() throws ElementNotFound {
        configuration.getSharing().setValidityDays(30);
        when(recipeService.getRecipeForUpdate(RECIPE_ID)).thenReturn(recipeWithId(RECIPE_ID));
        when(shareRepository.findByRecipeAndVisibility(any(), any())).thenReturn(Optional.empty());
        when(shareRepository.save(any())).thenAnswer(saved -> saved.getArgument(0));

        var share = cut().shareRecipePublicly(RECIPE_ID);

        assertEquals(NOW.plus(Duration.ofDays(30)), share.getExpiresAt());
    }

    @Test
    void aLapsedShareDoesNotResolveEvenBeforeTheCleanupJobHasRun() {
        var lapsed = liveShareOf(recipeWithId(RECIPE_ID));
        lapsed.setExpiresAt(NOW.minusSeconds(1));
        when(shareRepository.findById(SHARE_ID)).thenReturn(Optional.of(lapsed));

        assertThrows(ElementNotFound.class, () -> cut().resolveSharedRecipe(SHARE_ID));
    }

    @Test
    void openingASharedRecipeCountsTheView() throws ElementNotFound {
        when(shareRepository.findById(SHARE_ID))
                .thenReturn(Optional.of(liveShareOf(recipeWithId(RECIPE_ID))));
        when(recipeService.getRecipeById(RECIPE_ID)).thenReturn(recipeWithId(RECIPE_ID));

        cut().openSharedRecipe(SHARE_ID);

        verify(shareRepository, times(1)).incrementAccessCount(SHARE_ID);
    }

    @Test
    void anImageOfAnotherRecipeIsNotReachableThroughAShare() throws ElementNotFound {
        when(shareRepository.findById(SHARE_ID))
                .thenReturn(Optional.of(liveShareOf(recipeWithId(RECIPE_ID))));
        when(recipeService.getRecipeById(RECIPE_ID)).thenReturn(recipeWithId(RECIPE_ID));

        assertThrows(ElementNotFound.class,
                () -> cut().requireSharedImage(SHARE_ID, "an-image-of-some-other-recipe"));
    }

    private Recipe recipeWithId(Long id) {
        var owner = new CookpalUser();
        owner.setUserId(1L);
        return Recipe.builder().id(id).title("Shared recipe").owner(owner).build();
    }

    private Share liveShareOf(Recipe recipe) {
        return Share.builder()
                .id(SHARE_ID)
                .owner(new CookpalUser())
                .resourceType(ShareResourceType.RECIPE)
                .visibility(ShareVisibility.PUBLIC_LINK)
                .recipe(recipe)
                .expiresAt(NOW.plus(Duration.ofDays(1)))
                .accessCount(0)
                .build();
    }
}
