package com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import com.google.gson.Gson;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.IllegalFiletypeException;
import com.sterul.opencookbookapiserver.services.recipeimport.AbstractRecipeImporter;
import com.sterul.opencookbookapiserver.services.recipeimport.RecipeImportFailedException;
import com.sterul.opencookbookapiserver.util.IngredientUnitHelper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
public class RecipeScrapersWebserviceImporter extends AbstractRecipeImporter {

    @Autowired
    private RecipeScraperServiceProxy recipeScraperServiceProxy;

    @Autowired
    private IngredientUnitHelper unitHelper;

    private Gson gson = new Gson();

    @Override
    public Recipe importRecipe(String url, User owner) throws RecipeImportFailedException {
        ScrapedRecipe scrapedRecipe;
        try {
            var responseString = recipeScraperServiceProxy.scrapeRecipe(url);
            scrapedRecipe = gson.fromJson(responseString, ScrapedRecipe.class);
        } catch (IOException e) {
            throw new RecipeImportFailedException("Error from recipe scrape service", e);
        }

        Recipe importRecipe = Recipe.builder()
                .owner(owner)
                .title(scrapedRecipe.title)
                .preparationSteps(extractPraparationSteps(scrapedRecipe))
                .servings(Integer.parseInt(scrapedRecipe.yields.split(" ")[0]))
                .build();

        extractImage(owner, scrapedRecipe, importRecipe);
        extractIngredients(scrapedRecipe, importRecipe);

        return importRecipe;
    }

    private void extractIngredients(ScrapedRecipe scrapedRecipe, Recipe importRecipe) {
        var needs = scrapedRecipe.ingredients.stream().map((ingredient) -> {
            var parts = ingredient.split(" ");
            var textStartIndex = 0;

            Float amount;
            try {
                // TODO: Parse factions
                amount = Float.parseFloat(parts[textStartIndex]);
                textStartIndex++;
            } catch (NumberFormatException e) {
                amount = 0F;
            }

            var unit = parts[textStartIndex];
            if (unitHelper.isUnit(unit)) {
                textStartIndex++;
            } else {
                unit = "";
            }

            var ingredientText = String.join(" ", Arrays.copyOfRange(parts, textStartIndex, parts.length));

            return IngredientNeed.builder()
                    .amount(amount)
                    .unit(unit)
                    .ingredient(Ingredient.builder()
                            .name(ingredientText)
                            .build())
                    .build();
        }).toList();

        importRecipe.setNeededIngredients(needs);
    }

    private void extractImage(User owner, ScrapedRecipe scrapedRecipe, Recipe importRecipe) {
        if (scrapedRecipe.image != null) {

            try {
                var image = fetchImage(scrapedRecipe.image, owner);
                importRecipe.getImages().add(image);
            } catch (UnsupportedOperationException | IllegalFiletypeException | IOException e) {
                // Ignore image errros
            }
        }
    }

    private List<String> extractPraparationSteps(ScrapedRecipe scrapedRecipe) {
        var prepsteps = Arrays.asList(scrapedRecipe.instructions.replace("\r", "").split("\\n"));
        return prepsteps.stream().filter((step) -> {
            return step.length() > 0;
        }).toList();
    }

    @Data
    private class ScrapedRecipe {
        private String language;
        private String category;
        private String title;
        private String total_time;
        private String cook_time;
        private String prep_time;
        private String yields;
        private String image;
        private String author;
        private String instructions;
        private List<String> ingredients;
        private List<String> nutrients;
        private String ratings;
        private String cuisine;
        private String host;

    }

}
