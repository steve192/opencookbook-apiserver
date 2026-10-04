package com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.google.gson.JsonSyntaxException;
import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.services.RecipeImageService;
import com.sterul.opencookbookapiserver.services.recipeimport.AbstractRecipeImporter;
import com.sterul.opencookbookapiserver.services.recipeimport.RecipeImportFailedException;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@Transactional
public class RecipeScrapersWebserviceImporter extends AbstractRecipeImporter {

    private final RecipeScraperServiceProxy recipeScraperServiceProxy;
    private final IngredientExtractor ingredientExtractor;

    public RecipeScrapersWebserviceImporter(RecipeScraperServiceProxy recipeScraperServiceProxy,
            IngredientExtractor ingredientExtractor, RecipeImageService recipeImageService,
            OpencookbookConfiguration opencookbookConfiguration) {
        super(recipeImageService, opencookbookConfiguration);
        this.recipeScraperServiceProxy = recipeScraperServiceProxy;
        this.ingredientExtractor = ingredientExtractor;
    }

    @Override
    public Recipe importRecipe(String url, CookpalUser owner) {
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
            // A page the scraper only partly understood comes back with fields missing, so this
            // has to survive a null as readily as it survives "a few".
            servings = Integer.parseInt(scrapedRecipe.yields.split(" ")[0]);
            if (servings < 1) {
                // Servings must atleast be 1
                servings = 1;
            }
        } catch (NumberFormatException | NullPointerException e) {
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

        addImage(importRecipe, scrapedRecipe.image, owner);
        extractIngredients(scrapedRecipe, importRecipe);

        log.info("Recipe imported");
        return importRecipe;
    }

    private void extractIngredients(ScrapedRecipe scrapedRecipe, Recipe importRecipe) {
        importRecipe.setNeededIngredients(scrapedRecipe.ingredients.stream()
                .flatMap(line -> ingredientExtractor.toNeed(line).stream())
                .toList());
    }

    private List<String> extractPraparationSteps(ScrapedRecipe scrapedRecipe) {
        var prepsteps = Arrays.asList(scrapedRecipe.instructions.replace("\r", "").split("\\n"));
        return prepsteps.stream().filter(step -> !step.isEmpty()).toList();
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
