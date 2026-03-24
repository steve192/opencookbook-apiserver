package com.sterul.opencookbookapiserver.integration.cronjobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sterul.opencookbookapiserver.cronjobs.ImageDeletionJob;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.integration.IntegrationTest;
import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.RecipeImageService;

@SpringBootTest
@ActiveProfiles("integration-test")
class ImageDeletionJobTest extends IntegrationTest {

    @Autowired
    ImageDeletionJob cut;

    @MockitoBean
    RecipeImageService recipeImageService;

    @MockitoBean
    RecipeRepository recipeRepository;

    @MockitoBean
    RecipeImageRepository recipeImageRepository;

    RecipeImage oldRecipeImage;

    RecipeImage newReipceImage;

    Recipe testRecipe;

    @BeforeEach
    void setup() {
        oldRecipeImage = RecipeImage.builder().uuid("3284u398h2").build();
        oldRecipeImage.setCreatedOn(Instant.now().minus(100, ChronoUnit.DAYS));

        newReipceImage = RecipeImage.builder().uuid("3284u398nqw9ddh2").build();

        when(recipeImageRepository.findAllByCreatedOnBefore(any())).thenReturn(List.of(oldRecipeImage));
    }

    private void whenRecipeWithImagesExists(List<RecipeImage> images) {
        testRecipe = Recipe.builder()
                .id(1L)
                .images(images)
                .build();

        when(recipeRepository.findAll()).thenReturn(List.of(testRecipe));
    }

    @Test
    void unlinkedOldImagesAreDeleted() throws IOException {
        whenRecipeWithImagesExists(List.of(newReipceImage));

        cut.deleteUnlinkedImages();

        verify(recipeImageService, times(1)).deleteImage(oldRecipeImage.getUuid());
    }

    @Test
    void noImagesAreDeletedWhenAllAreLinked() throws IOException {
        whenRecipeWithImagesExists(List.of(newReipceImage, oldRecipeImage));

        cut.deleteUnlinkedImages();

        verify(recipeImageService, times(0)).deleteImage(any());
    }

}
