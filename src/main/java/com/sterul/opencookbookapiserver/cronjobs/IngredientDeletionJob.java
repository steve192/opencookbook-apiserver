package com.sterul.opencookbookapiserver.cronjobs;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableScheduling
@Slf4j
public class IngredientDeletionJob {

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Scheduled(cron = "0 0 0/24 * * *")
    @Transactional
    public void deleteUnlinkedIngredients() {
        log.info("Starting ingredient cleanup job");
        var allOldIngredients = ingredientRepository.findAllByIsPublicIngredientAndCreatedOnBefore(false,
                Instant.now().minus(1, ChronoUnit.DAYS));

        var allRecipes = recipeRepository.findAll();

        allRecipes.forEach(recipe -> recipe.getNeededIngredients().forEach(usedIngredient -> allOldIngredients
                .removeIf(ingredient -> ingredient.getId() == usedIngredient.getIngredient().getId())));

        allOldIngredients.forEach(oldIngredient -> log.info("Removing unused ingredient {} of user {}", oldIngredient,
                oldIngredient.getOwner()));

        ingredientRepository.deleteAll(allOldIngredients);
    }
}
