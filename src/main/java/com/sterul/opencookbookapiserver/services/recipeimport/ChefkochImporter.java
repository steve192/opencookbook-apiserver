package com.sterul.opencookbookapiserver.services.recipeimport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.IllegalFiletypeException;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
public class ChefkochImporter extends AbstractRecipeImporter {

    @Override
    public Recipe importRecipe(String url, CookpalUser owner) throws RecipeImportFailedException {
        var importRecipe = Recipe.builder().build();
        var recipeId = url.split("//")[1].split("/")[2];

        var request = new HttpGet("https://api.chefkoch.de/v2/aggregations/recipe/public/screen-v4/" + recipeId);

        ChefkochPublicRecipe publicRecipe;
        try {
            var response = client.execute(request);
            var jsonString = EntityUtils.toString(response.getEntity(), "UTF-8");
            publicRecipe = gson.fromJson(jsonString, ChefkochPublicRecipe.class);
        } catch (IOException e) {
            throw new RecipeImportFailedException();
        }

        extractGeneralInformation(importRecipe, publicRecipe);
        extractPreparationSteps(importRecipe, publicRecipe);

        try {
            extractAndSaveImages(importRecipe, recipeId, publicRecipe, owner);
        } catch (IOException e) {
            throw new RecipeImportFailedException();
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

                // TODO: Fetch ingredients form ingredient service
                var importIngredient = Ingredient.builder()
                        .name(ingredient.name)
                        .build();

                var importIngredientNeed = IngredientNeed.builder()
                        .ingredient(importIngredient)
                        .amount(ingredient.amount)
                        .unit(ingredient.unit)
                        .build();

                importedRecipe.getNeededIngredients().add(importIngredientNeed);
            }
        }
    }

    private void extractAndSaveImages(Recipe importedRecipe, String recipeId, ChefkochPublicRecipe publicRecipe,
            CookpalUser owner) throws IOException {
        importedRecipe.setImages(new ArrayList<>());
        for (var image : publicRecipe.recipeImages) {
            try {
                var fetchedImage = fetchImage(
                        "https://api.chefkoch.de/v2/recipes/" + recipeId + "/images/" + image.id + "/crop-960x640",
                        owner);
                importedRecipe.getImages().add(fetchedImage);
            } catch (UnsupportedOperationException | IllegalFiletypeException | IOException e) {
                // Error fetching image, ignore
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
