package com.sterul.opencookbookapiserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import com.sterul.opencookbookapiserver.controllers.RecipeShareController;
import com.sterul.opencookbookapiserver.controllers.exceptions.NotAuthorizedException;
import com.sterul.opencookbookapiserver.controllers.requests.ShareRequest;
import com.sterul.opencookbookapiserver.controllers.responses.RecipeShareResponse;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.RecipeService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

@SpringBootTest
@ActiveProfiles("integration-test")
public class RecipeShareControllerTest extends IntegrationTest {

    @Autowired
    private RecipeShareController cut;

    @MockBean
    private RecipeService recipeService;

    @Autowired
    private UserRepository userRepository;

    private CookpalUser testUser;

    @BeforeEach
    void setup() {
        TestUtils.whenAuthenticated(userRepository);
        testUser = TestUtils.getTestUser(userRepository);
    }

    private ShareRequest createShareRequest(Long recipeId) {
        return ShareRequest.builder()
                .recipeId(recipeId)
                .build();
    }

    @Test
    public void testShareRecipeWithValidPermissions() throws Exception {
        // Arrange
        Recipe mockRecipe = Recipe.builder().id(1L).title("Mock Recipe").owner(testUser).build();
        when(recipeService.getRecipeById(1L)).thenReturn(mockRecipe);
        when(recipeService.hasAccessPermissionToRecipe(1L, testUser)).thenReturn(true);

        // Act
        RecipeShareResponse response = cut.sharePublicly(createShareRequest(1L));

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getShareId()).isNotEmpty();
    }

    @Test
    public void testShareRecipeWithoutPermissions() throws Exception {
        // Arrange
        Recipe mockRecipe = Recipe.builder().id(2L).title("Mock Recipe").owner(testUser).build();
        when(recipeService.getRecipeById(2L)).thenReturn(mockRecipe);
        when(recipeService.hasAccessPermissionToRecipe(2L, testUser)).thenReturn(false);

        // Act & Assert
        try {
            cut.sharePublicly(createShareRequest(2L));
        } catch (NotAuthorizedException e) {
            assertThat(e).isInstanceOf(NotAuthorizedException.class);
        }
    }

    @Test
    public void testShareNonExistentRecipe() throws Exception {
        // Arrange
        when(recipeService.getRecipeById(anyLong())).thenThrow(new ElementNotFound());

        // Act & Assert
        try {
            cut.sharePublicly(createShareRequest(999L));
        } catch (ElementNotFound e) {
            assertThat(e).isInstanceOf(ElementNotFound.class);
        }
    }

    @Test
    public void testShareRecipeWithInvalidData() {
        // Arrange
        ShareRequest invalidRequest = new ShareRequest(); // Missing required recipeId

        // Act & Assert
        try {
            cut.sharePublicly(invalidRequest);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
