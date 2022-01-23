package com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.gson.JsonSyntaxException;
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
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class RecipeScrapersWebserviceImporter extends AbstractRecipeImporter {

    @Autowired
    private RecipeScraperServiceProxy recipeScraperServiceProxy;

    @Autowired
    private IngredientUnitHelper unitHelper;

    @Override
    public Recipe importRecipe(String url, User owner) throws RecipeImportFailedException {
        log.info("Importing recipe " + url);
        ScrapedRecipe scrapedRecipe;
        try {
            var responseString = recipeScraperServiceProxy.scrapeRecipe(url);
            scrapedRecipe = gson.fromJson(responseString, ScrapedRecipe.class);
        } catch (IOException e) {
            throw new RecipeImportFailedException("Error in communication with scrape service", e);
        } catch (JsonSyntaxException e) {
            throw new RecipeImportFailedException("Error parsing response from scrape service", e);
        }
        log.info("Parsing response");

        Recipe importRecipe = Recipe.builder()
                .owner(owner)
                .title(scrapedRecipe.title)
                .preparationSteps(extractPraparationSteps(scrapedRecipe))
                .servings(Integer.parseInt(scrapedRecipe.yields.split(" ")[0]))
                .build();

        extractImage(owner, scrapedRecipe, importRecipe);
        extractIngredients(scrapedRecipe, importRecipe);

        log.info("Recipe imported");
        return importRecipe;
    }

    private void extractIngredients(ScrapedRecipe scrapedRecipe, Recipe importRecipe) {
        var needs = scrapedRecipe.ingredients.stream().map(ingredient -> {
            var parts = ingredient.split(" ");
            var textStartIndex = 0;

            Float amount;
            try {
                amount = Float.parseFloat(parts[textStartIndex]);
                textStartIndex++;
            } catch (NumberFormatException e) {
                // Amount could be represented as a "vulgar fraction". Split this unicode symbol
                // into 3 parts: counter, dash, divider
                var normalizedAmount = Normalizer.normalize(parts[textStartIndex], Normalizer.Form.NFKD);
                if (normalizedAmount.contains("\u2044")) {
                    // 2044 = unicode dash
                    var fractionParts = normalizedAmount.split("\u2044");
                    amount = (float) Integer.parseInt(fractionParts[0]) / Integer.parseInt(fractionParts[1]);
                    textStartIndex++;
                } else {
                    amount = 0F;
                }
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
        log.info("Fetching image if present " + scrapedRecipe.image);
        if (scrapedRecipe.image != null) {

            try {
                var image = fetchImage(scrapedRecipe.image, owner);
                importRecipe.getImages().add(image);
            } catch (UnsupportedOperationException | IllegalFiletypeException | IOException e) {
                // Ignore image errros
                log.error("Error importing recipe image from " + scrapedRecipe.image);
            }
            log.info("Image fetched:" + importRecipe.getImages().size());
        }
    }

    private List<String> extractPraparationSteps(ScrapedRecipe scrapedRecipe) {
        var prepsteps = Arrays.asList(scrapedRecipe.instructions.replace("\r", "").split("\\n"));
        return prepsteps.stream().filter(step -> step.length() > 0).toList();
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
        private Nutrients nutrients;
        private String ratings;
        private String cuisine;
        private String host;

    }

    @Data
    private class Nutrients {
        private String calories;
        private String servingSize;
    }

    @Override
    public List<String> getSupportedHostnames() {
        try {
            return gson.fromJson(recipeScraperServiceProxy.getSupportedHosts(), ArrayList.class);
        } catch (JsonSyntaxException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

}
