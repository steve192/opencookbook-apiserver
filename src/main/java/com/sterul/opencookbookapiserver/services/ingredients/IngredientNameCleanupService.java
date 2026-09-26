package com.sterul.opencookbookapiserver.services.ingredients;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.projections.RecipeLine;
import com.sterul.opencookbookapiserver.services.ingredients.IngredientNameSplitter.SplitName;
import com.sterul.opencookbookapiserver.services.nutrition.IngredientNames;

import lombok.extern.slf4j.Slf4j;

/**
 * Moves amounts and units left in ingredient names by old imports into their recipe lines. The name's amount wins.
 * An ingredient whose cleaned name the owner already has is merged into that one.
 */
@Service
@Transactional(rollbackFor = ApiException.class)
@Slf4j
public class IngredientNameCleanupService {

    private final IngredientRepository ingredientRepository;
    private final RecipeRepository recipeRepository;
    private final IngredientNameSplitter splitter;

    public IngredientNameCleanupService(IngredientRepository ingredientRepository, RecipeRepository recipeRepository,
            IngredientNameSplitter splitter) {
        this.ingredientRepository = ingredientRepository;
        this.recipeRepository = recipeRepository;
        this.splitter = splitter;
    }

    public record LineChange(Long recipeId, String recipeTitle, Float amount, String unit, Float newAmount, String newUnit) {
    }

    /** @param mergesIntoIngredientId null if only renamed */
    public record Proposal(Long ingredientId, String ownerEmailAddress, Instant lastChange, String name, SplitName split,
            Long mergesIntoIngredientId, List<LineChange> lines) {
    }

    /** @param name as proposed or corrected by the administrator */
    public record Decision(Long ingredientId, String name, Instant lastChange) {
    }

    public record Outcome(int renamed, int merged, int linesChanged, int skipped) {
    }

    @Transactional(readOnly = true)
    public List<Proposal> preview() {
        var ingredients = ingredientRepository.findAllWithOwnerAndFood();
        var byOwnerAndName = ingredients.stream()
                .collect(Collectors.toMap(ingredient -> ownerAndName(ingredient.getOwner().getUserId(), ingredient.getName()),
                        Ingredient::getId, (first, second) -> first));
        var splits = ingredients.stream()
                .flatMap(ingredient -> splitter.split(ingredient.getName()).map(split -> Map.entry(ingredient, split)).stream())
                .toList();
        var lines = linesByIngredient(splits.stream().map(entry -> entry.getKey().getId()).toList());
        return splits.stream()
                .map(entry -> {
                    var ingredient = entry.getKey();
                    var split = entry.getValue();
                    return new Proposal(ingredient.getId(), ingredient.getOwner().getEmailAddress(), ingredient.getLastChange(),
                            ingredient.getName(), split,
                            byOwnerAndName.get(ownerAndName(ingredient.getOwner().getUserId(), split.name())),
                            lines.getOrDefault(ingredient.getId(), List.of()).stream()
                                    .map(line -> new LineChange(line.recipeId(), line.recipeTitle(), line.need().getAmount(),
                                            line.need().getUnit(), newAmount(line, split), newUnit(line, split)))
                                    .toList());
                })
                .toList();
    }

    /** Skips ingredients gone, changed since the preview, or clean by now. */
    public Outcome apply(List<Decision> decisions) {
        var renamed = 0;
        var merged = 0;
        var linesChanged = 0;
        var skipped = 0;
        for (var decision : decisions) {
            var name = decision.name() == null ? "" : IngredientNames.tidy(decision.name());
            if (name.isEmpty()) {
                throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "Ingredient " + decision.ingredientId() + " needs a name");
            }
            var ingredient = ingredientRepository.findById(decision.ingredientId()).orElse(null);
            var split = ingredient == null ? null : splitter.split(ingredient.getName()).orElse(null);
            if (split == null || !Objects.equals(ingredient.getLastChange(), decision.lastChange())) {
                skipped++;
                continue;
            }
            var lines = recipeRepository.findLinesUsingIngredients(List.of(ingredient.getId()));
            for (var line : lines) {
                line.need().setAmount(newAmount(line, split));
                line.need().setUnit(newUnit(line, split));
            }
            linesChanged += lines.size();
            var existing = ingredientRepository.findByNameAndOwner(name, ingredient.getOwner())
                    .filter(other -> !other.getId().equals(ingredient.getId()));
            if (existing.isPresent()) {
                merge(ingredient, existing.get(), lines);
                merged++;
            } else {
                ingredient.setName(name);
                renamed++;
            }
        }
        log.info("Admin: Cleaned ingredient names: {} renamed, {} merged, {} recipe lines changed, {} skipped", renamed, merged,
                linesChanged, skipped);
        return new Outcome(renamed, merged, linesChanged, skipped);
    }

    private void merge(Ingredient merged, Ingredient kept, Collection<RecipeLine> lines) {
        lines.forEach(line -> line.need().setIngredient(kept));
        kept.adoptLinkOf(merged);
        merged.getPortionOverrides().forEach(kept.getPortionOverrides()::putIfAbsent);
        ingredientRepository.delete(merged);
    }

    private Map<Long, List<RecipeLine>> linesByIngredient(Collection<Long> ingredientIds) {
        return recipeRepository.findLinesUsingIngredients(ingredientIds).stream()
                .collect(Collectors.groupingBy(line -> line.need().getIngredient().getId()));
    }

    private static Float newAmount(RecipeLine line, SplitName split) {
        return split.amount() != null ? split.amount() : line.need().getAmount();
    }

    private static String newUnit(RecipeLine line, SplitName split) {
        return split.unit() != null ? split.unit() : line.need().getUnit();
    }

    private static String ownerAndName(Long ownerId, String name) {
        return ownerId + " " + name;
    }
}
