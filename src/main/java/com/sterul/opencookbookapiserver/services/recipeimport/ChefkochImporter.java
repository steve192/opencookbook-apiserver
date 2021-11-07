package com.sterul.opencookbookapiserver.services.recipeimport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import com.google.gson.Gson;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.Recipe;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.IllegalFiletypeException;
import com.sterul.opencookbookapiserver.services.RecipeImageService;
import com.sterul.opencookbookapiserver.services.RecipeService;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChefkochImporter implements IRecipeImporter {

    private CloseableHttpClient client;
    private Gson gson;

    @Autowired
    private RecipeImageService recipeImageService;

    @Autowired
    private RecipeService recipeService;

    public ChefkochImporter() {
        client = HttpClientBuilder.create().build();
        gson = new Gson();
    }

    @Override
    public Recipe importRecipe(String url) throws RecipeImportFailedException {
        var importRecipe = new Recipe();

        var recipeId = url.split("//")[1].split("/")[2];

        var request = new HttpGet("https://api.chefkoch.de/v2/aggregations/recipe/public/screen-v4/" + recipeId);

        ChefkocPublicRecipe publicRecipe;
        try {
            var response = client.execute(request);
            var jsonString = EntityUtils.toString(response.getEntity(), "UTF-8");
            publicRecipe = gson.fromJson(jsonString, ChefkocPublicRecipe.class);
        } catch (IOException e) {
            throw new RecipeImportFailedException();
        }

        extractGeneralInformation(importRecipe, publicRecipe);
        extractPreparationSteps(importRecipe, publicRecipe);

        try {
            extractAndSaveImages(importRecipe, recipeId, publicRecipe);
        } catch (IOException e) {
            throw new RecipeImportFailedException();
        }
        extractIngredientNeeds(importRecipe, publicRecipe);

        recipeService.createNewRecipe(importRecipe);

        return importRecipe;
    }

    private void extractIngredientNeeds(Recipe importedRecipe, ChefkocPublicRecipe publicRecipe) {
        importedRecipe.setNeededIngredients(new ArrayList<IngredientNeed>());
        for (var ingredientGroup : publicRecipe.recipe.ingredientGroups) {
            // Ingredient groups are not supported for now, just import all
            for (var ingredient : ingredientGroup.ingredients) {

                // TODO: Fetch ingredients form ingredient service
                var importIngredient = new Ingredient();
                importIngredient.setName(ingredient.name);

                var importIngredientNeed = new IngredientNeed();
                importIngredientNeed.setIngredient(importIngredient);
                importIngredientNeed.setAmount(ingredient.amount);
                importIngredientNeed.setUnit(ingredient.unit);

                importedRecipe.getNeededIngredients().add(importIngredientNeed);
            }
        }
    }

    private void extractAndSaveImages(Recipe importedRecipe, String recipeId, ChefkocPublicRecipe publicRecipe)
            throws IOException, ClientProtocolException {
        importedRecipe.setImages(new ArrayList<RecipeImage>());
        for (var image : publicRecipe.recipeImages) {
            var request2 = new HttpGet(
                    "https://api.chefkoch.de/v2/recipes/" + recipeId + "/images/" + image.id + "/crop-960x640");
            var response2 = client.execute(request2);

            try {
                var importImage = recipeImageService.saveNewImage(response2.getEntity().getContent(),
                        response2.getEntity().getContentLength());
                importedRecipe.getImages().add(importImage);
            } catch (UnsupportedOperationException | IllegalFiletypeException e) {
                // Images cannot be added, ignore
            }

        }
    }

    private void extractGeneralInformation(Recipe importedRecipe, ChefkocPublicRecipe publicRecipe) {
        importedRecipe.setTitle(publicRecipe.recipe.title);
    }

    private void extractPreparationSteps(Recipe importedRecipe, ChefkocPublicRecipe publicRecipe) {
        // Depending on which os the recipe was created it contains \n or \r\n line breaks
        publicRecipe.recipe.instructions.replace("\r", "");
        var possibleRecipeSteps = publicRecipe.recipe.instructions.split("\n");
        if (possibleRecipeSteps.length == 0) {
            importedRecipe.setPreparationSteps(Arrays.asList(publicRecipe.recipe.instructions));
        } else {
            var instructions = new ArrayList<String>(Arrays.asList(possibleRecipeSteps));
            instructions.removeIf(step -> step.equals(""));
            importedRecipe.setPreparationSteps(instructions);
        }
    }

    private class ChefkocPublicRecipe {

        ChefkochRecipe recipe;

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

            private class IngredientGroup {
                Ingredient[] ingredients;

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

}
