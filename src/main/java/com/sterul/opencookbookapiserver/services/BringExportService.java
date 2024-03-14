package com.sterul.opencookbookapiserver.services;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.BringExport;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.BringExportRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
public class BringExportService {

    private RecipeService recipeService;
    private BringExportRepository bringExportRepository;

    public BringExportService(RecipeService recipeService, BringExportRepository bringExportRepository) {
        this.recipeService = recipeService;
        this.bringExportRepository = bringExportRepository;
    }

    public void deleteExpiredExports() {
        var allExports = bringExportRepository.findAll();
        allExports.stream().filter(export -> export.getCreatedOn().plusSeconds(300).isBefore(Instant.now()))
                .forEach(expiredExport -> {
                    bringExportRepository.delete(expiredExport);
                    log.info("Deleting expired bring export {} ({})", expiredExport.getId(),
                            expiredExport.getOwner().getEmailAddress());
                });
    }

    public BringExport createBringExport(Long recipeId, CookpalUser user) throws ElementNotFound {
        var recipe = recipeService.getRecipeById(recipeId);

        var bringExport = BringExport.builder().baseAmount(recipe.getServings()).owner(user)
                .ingredients(recipe.getNeededIngredients().stream()
                        .map(ingredient -> ingredient.getAmount().toString().replace(".0", "") + " "
                                + ingredient.getUnit() + " "
                                + ingredient.getIngredient().getName())
                        .toList())
                .build();

        return bringExportRepository.save(bringExport);
    }

    public BringExport getBringExport(String bringExportId) throws ElementNotFound {
        var export = bringExportRepository.findById(bringExportId).orElseThrow(ElementNotFound::new);
        if (export.getCreatedOn().plusSeconds(300).isBefore(Instant.now())) {
            // Not valid anymore
            throw new ElementNotFound();
        }
        return export;
    }

    record BringExportIngredient(String name) {

    }

}
