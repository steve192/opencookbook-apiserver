package com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.google.gson.JsonSyntaxException;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.IllegalFiletypeException;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.recipeimport.AbstractRecipeImporter;
import com.sterul.opencookbookapiserver.services.recipeimport.ImportNotSupportedException;
import com.sterul.opencookbookapiserver.services.recipeimport.RecipeImportFailedException;
import com.sterul.opencookbookapiserver.util.IngredientUnitHelper;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@Transactional
public class RecipeScrapersWebserviceImporter extends AbstractRecipeImporter {

    @Autowired
    private RecipeScraperServiceProxy recipeScraperServiceProxy;

    @Autowired
    private IngredientUnitHelper unitHelper;

    @Autowired
    private IngredientService ingredientService;

    @Override
    public Recipe importRecipe(String url, CookpalUser owner) throws RecipeImportFailedException, ImportNotSupportedException {
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

        Long prepTime;
        try {
            prepTime = Long.valueOf(scrapedRecipe.prep_time);
        } catch (NumberFormatException e) {
            prepTime = 0L;
        }

        Long totalTime;
        try {
            totalTime = Long.valueOf(scrapedRecipe.total_time);
        } catch (NumberFormatException e) {
            totalTime = 0L;
        }

        Integer servings;
        try {
            servings = Integer.parseInt(scrapedRecipe.yields.split(" ")[0]);
            if (servings == null || servings < 1) {
                // Servings must atleast be 1
                servings = 1;
            }
        } catch (NumberFormatException e) {
            servings = 1;
        }
        Recipe importRecipe = Recipe.builder()
                .owner(owner)
                .title(scrapedRecipe.title)
                .preparationSteps(extractPraparationSteps(scrapedRecipe))
                .servings(servings)
                .preparationTime(prepTime)
                .totalTime(totalTime)
                .recipeSource(url)
                .build();

        extractImage(owner, scrapedRecipe, importRecipe);
        extractIngredients(scrapedRecipe, importRecipe, owner);

        log.info("Recipe imported");
        return importRecipe;
    }

    private void extractIngredients(ScrapedRecipe scrapedRecipe, Recipe importRecipe, CookpalUser owner) {
        var needs = scrapedRecipe.ingredients.stream().map(ingredient -> {
            var unit = IngredientExtractor.extractUnit(ingredient);
            var amount = IngredientExtractor.extractAmount(ingredient);
            var name = IngredientExtractor.extractName(ingredient);
            var additionalInfo = IngredientExtractor.extractAdditionalInfo(ingredient);

            Ingredient newIngredient;
            try {
                newIngredient = ingredientService.findUserIngredientBySimilarName(name, owner);
            } catch (ElementNotFound e) {
                newIngredient = Ingredient.builder()
                        .name(name)
                        .build();
            }
            return IngredientNeed.builder()
                    .amount(amount)
                    .unit(unit)
                    .ingredient(newIngredient)
                    .build();
        }).toList();

        importRecipe.setNeededIngredients(needs);
    }

    private void extractImage(CookpalUser owner, ScrapedRecipe scrapedRecipe, Recipe importRecipe) {
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

    @Override
    public List<String> getSupportedHostnames() throws IOException {
        try {
            return gson.fromJson(recipeScraperServiceProxy.getSupportedHosts(), ArrayList.class);
        } catch (JsonSyntaxException e) {
            throw new IOException("Error parsing response from recipe scrapers service", e);
        } catch (IOException e) {
            throw new IOException("Error in communication with recipe scrapers service", e);
        }
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

}
