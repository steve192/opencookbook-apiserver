package com.sterul.opencookbookapiserver.cronjobs;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.RecipeImageService;

import lombok.extern.slf4j.Slf4j;

@EnableScheduling
@Configuration
@Slf4j
public class ImageDeletionJob {
    @Autowired
    RecipeImageRepository recipeImageRepository;

    @Autowired
    RecipeRepository recipeRepository;

    @Autowired
    RecipeImageService recipeImageService;

    @Scheduled(cron = "0 0 0/24 * * *")
    @Transactional
    public void deleteUnlinkedImages() {
        log.info("Running image deletion job");
        var allRecipes = recipeRepository.findAll();

        var allUsedImages = new ArrayList<String>();

        allRecipes.forEach(recipe -> recipe.getImages().forEach(image -> allUsedImages.add(image.getUuid())));

        var allOldImages = recipeImageRepository.findAllByCreatedOnBefore(Instant.now().minus(1, ChronoUnit.DAYS));
        allOldImages.forEach(image -> {
            var imageUsed = allUsedImages.stream().anyMatch(usedImage -> usedImage.equals(image.getUuid()));
            if (!imageUsed) {
                try {
                    log.info("delete image in deletion job {}", image.getUuid());
                    recipeImageService.deleteImage(image.getUuid());
                } catch (IOException e) {
                    log.error("Error deleting image {} in scheduled deletion job",
                            image.getUuid(), e);
                }
            }
        });
    }

}