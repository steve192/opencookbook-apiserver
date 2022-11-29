package com.sterul.opencookbookapiserver.cronjobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.Test;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.RecipeImageService;

@SpringBootTest
@ActiveProfiles("test")
public class ImageDeletionJobTest {

    @Autowired
    ImageDeletionJob cut;

    @MockBean
    RecipeImageService recipeImageService;

    @MockBean
    RecipeRepository recipeRepository;

    @Mock
    RecipeImageRepository recipeImageRepository;

    @Mock
    RecipeImage oldRecipeImage;

    @Mock
    RecipeImage newReipceImage;

    @Mock
    Recipe testRecipe;

    @BeforeAll
    void setup() {
        when(oldRecipeImage.getCreatedOn()).thenReturn(Instant.now().minus(100, ChronoUnit.DAYS));
        when(oldRecipeImage.getUuid()).thenReturn("3284u398h2");

        when(newReipceImage.getCreatedOn()).thenReturn(Instant.now().minus(100, ChronoUnit.SECONDS));
        when(newReipceImage.getUuid()).thenReturn("3284u398h2");

        when(recipeImageRepository.findAllBycreatedOnBefore(any())).thenReturn(List.of(oldRecipeImage));
        when(recipeImageRepository.findAll()).thenReturn(List.of(oldRecipeImage, newReipceImage));
    }

    private void whenRecipeWithImagesExists(List<RecipeImage> images) {
        testRecipe = Recipe.builder()
                .id(1L)
                .images(images)
                .build();

        when(recipeRepository.findAll()).thenReturn(List.of(testRecipe));
    }

    @Test
    public void unlinkedOldImagesAreDeleted() throws IOException {
        whenRecipeWithImagesExists(List.of(newReipceImage));

        cut.deleteUnlinkedImages();

        verify(recipeImageService, times(1)).deleteImage(oldRecipeImage.getUuid());
    }

    @Test
    public void noImagesAreDeletedWhenAllAreLinked() throws IOException {
        whenRecipeWithImagesExists(List.of(newReipceImage, oldRecipeImage));

        cut.deleteUnlinkedImages();

        verify(recipeImageService, times(0)).deleteImage(any());
    }

}
