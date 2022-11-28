package com.sterul.opencookbookapiserver.cronjobs;

import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.RecipeImageService;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableScheduling
@Slf4j
public class ImageDeletionJob {
    @Autowired
    RecipeImageRepository recipeImageRepository;

    @Autowired
    RecipeRepository recipeRepository;

    @Autowired
    RecipeImageService recipeImageService;

    @Scheduled(fixedDelay = 3600000)
    public void deleteUnlinkedImages() {
        var allRecipes = recipeRepository.findAll();

        var allUsedImages = new ArrayList<String>();

        allRecipes.forEach((recipe) -> recipe.getImages().forEach((image) -> {
            allUsedImages.add(image.getUuid());
        }));

        var allImages = recipeImageRepository.findAll();
        allImages.forEach((image) -> {
            var imageUsed = allUsedImages.stream().filter((usedImage) -> usedImage.equals(image.getUuid())).findFirst()
                    .isPresent();
            if (!imageUsed) {
                // try {
                log.info("delete image in deletion job {}", image.getUuid());
                // recipeImageService.deleteImage(image.getUuid());
                // } catch (IOException e) {
                // log.error("Error deleting image {} in scheduled deletion job",
                // image.getUuid(), e);
                // }
            }
        });
    }

}
