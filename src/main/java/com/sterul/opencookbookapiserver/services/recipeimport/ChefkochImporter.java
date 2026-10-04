package com.sterul.opencookbookapiserver.services.recipeimport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.RecipeImageService;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
public class ChefkochImporter extends AbstractRecipeImporter {

    public ChefkochImporter(RecipeImageService recipeImageService,
            OpencookbookConfiguration opencookbookConfiguration) {
        super(recipeImageService, opencookbookConfiguration);
    }

    @Override
    public Recipe importRecipe(String url, CookpalUser owner) {
        var importRecipe = Recipe.builder().build();
        var recipeId = url.split("//")[1].split("/")[2];

        var request = new HttpGet("https://api.chefkoch.de/v2/aggregations/recipe/public/screen-v4/" + recipeId);

        ChefkochPublicRecipe publicRecipe;
        try {
            var jsonString = client.execute(request,
                    response -> EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            publicRecipe = gson.fromJson(jsonString, ChefkochPublicRecipe.class);
        } catch (IOException e) {
            throw new RecipeImportFailedException("Could not read recipe " + recipeId, e);
        }

        extractGeneralInformation(importRecipe, publicRecipe);
        extractPreparationSteps(importRecipe, publicRecipe);
        for (var image : publicRecipe.recipeImages) {
            addImage(importRecipe,
                    "https://api.chefkoch.de/v2/recipes/" + recipeId + "/images/" + image.id + "/crop-960x640", owner);
        }
        extractIngredientNeeds(importRecipe, publicRecipe);

        importRecipe.setRecipeGroups(new ArrayList<>());
        importRecipe.setOwner(owner);

        return importRecipe;
    }

    private void extractIngredientNeeds(Recipe importedRecipe, ChefkochPublicRecipe publicRecipe) {
        importedRecipe.setNeededIngredients(new ArrayList<>());
        for (var ingredientGroup : publicRecipe.recipe.ingredientGroups) {
            // Ingredient groups are not supported for now, just import all
            for (var ingredient : ingredientGroup.ingredients) {
                importedRecipe.getNeededIngredients()
                        .add(IngredientNeed.detached(ingredient.amount, ingredient.unit, ingredient.name, null));
            }
        }
    }

    private void extractGeneralInformation(Recipe importedRecipe, ChefkochPublicRecipe publicRecipe) {
        importedRecipe.setTitle(publicRecipe.recipe.title);
        importedRecipe.setServings(publicRecipe.recipe.servings);
    }

    private void extractPreparationSteps(Recipe importedRecipe, ChefkochPublicRecipe publicRecipe) {
        // Depending on which os the recipe was created it contains \n or \r\n line
        // breaks
        var instructionString = publicRecipe.recipe.instructions.replace("\r", "");
        var possibleRecipeSteps = instructionString.split("\n");
        if (possibleRecipeSteps.length == 0) {
            importedRecipe.setPreparationSteps(Arrays.asList(publicRecipe.recipe.instructions));
        } else {
            var instructions = new ArrayList<String>(Arrays.asList(possibleRecipeSteps));
            instructions.removeIf(step -> step.equals(""));
            importedRecipe.setPreparationSteps(instructions);
        }
    }

    private class ChefkochPublicRecipe {

        ChefkochRecipe recipe;

        @Data
        private class ChefkochRecipe {
            String title;
            Integer preparationTime;
            Integer difficulty;
            String siteUrl;
            Integer restingTime;
            Integer cookingTime;
            Integer totalTime;
            Integer kCalories;
            Integer servings;
            String instructions;

            IngredientGroup[] ingredientGroups;

            @Data
            private class IngredientGroup {
                Ingredient[] ingredients;

                @Data
                private class Ingredient {
                    String name;
                    String usageInfo;
                    String unit;
                    Float amount;
                }
            }
        }

        RecipeImage[] recipeImages;

        private class RecipeImage {
            String id;
        }
    }

    @Override
    public List<String> getSupportedHostnames() {
        return Arrays.asList("chefkoch.de");
    }

}
