package com.sterul.opencookbookapiserver.cronjobs;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.sterul.opencookbookapiserver.repositories.IngredientRepository;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/** A day's grace: the editor may create an ingredient before its recipe is saved. */
@Configuration
@EnableScheduling
@Slf4j
public class IngredientDeletionJob {

    private final IngredientRepository ingredientRepository;

    public IngredientDeletionJob(IngredientRepository ingredientRepository) {
        this.ingredientRepository = ingredientRepository;
    }

    @Scheduled(cron = "0 0 0/24 * * *")
    @Transactional
    public void deleteUnlinkedIngredients() {
        log.info("Starting ingredient cleanup job");
        var unused = ingredientRepository.findUnusedCreatedBefore(Instant.now().minus(1, ChronoUnit.DAYS));
        unused.forEach(ingredient -> log.info("Removing unused ingredient {} of user {}", ingredient, ingredient.getOwner()));
        ingredientRepository.deleteAll(unused);
    }
}
