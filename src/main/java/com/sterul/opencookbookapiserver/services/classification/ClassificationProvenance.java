package com.sterul.opencookbookapiserver.services.classification;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.recipe.ClassificationKind;
import com.sterul.opencookbookapiserver.entities.recipe.DerivedClassification;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeClassificationRun;
import com.sterul.opencookbookapiserver.repositories.DerivedClassificationRepository;

/**
 * Which of a recipe's values a run derived. Anything not marked is a person's, including every
 * value set before runs existed, so a code path that knows nothing about classification can never
 * hand a person's choice to a run.
 */
@Component
public class ClassificationProvenance {

    private final DerivedClassificationRepository repository;
    private final List<ClassifiedAttribute<?>> attributes;

    public ClassificationProvenance(DerivedClassificationRepository repository, List<ClassifiedAttribute<?>> attributes) {
        this.repository = repository;
        this.attributes = attributes;
    }

    /** A recipe's classified values as they stand, encoded, to tell afterwards which ones a person changed. */
    public record Snapshot(Map<ClassificationKind, String> values) {
    }

    /** Every mark of a kind, by recipe id. */
    public Map<Long, DerivedClassification> allOf(ClassificationKind kind) {
        return repository.findAllOfKind(kind).stream()
                .collect(Collectors.toMap(mark -> mark.getRecipe().getId(), Function.identity()));
    }

    /** The run that derived the recipe's current value; empty where a person chose it. */
    public Optional<RecipeClassificationRun> derivedBy(Recipe recipe, ClassificationKind kind) {
        return repository.findByRecipeAndKind(recipe, kind).map(DerivedClassification::getRun);
    }

    /** @param run null where the value is a person's again */
    public void attribute(Recipe recipe, ClassificationKind kind, RecipeClassificationRun run) {
        var existing = repository.findByRecipeAndKind(recipe, kind);
        if (run == null) {
            existing.ifPresent(repository::delete);
            return;
        }
        var mark = existing.orElseGet(() -> DerivedClassification.builder().recipe(recipe).kind(kind).build());
        mark.setRun(run);
        repository.save(mark);
    }

    public Snapshot snapshot(Recipe recipe) {
        var values = new EnumMap<ClassificationKind, String>(ClassificationKind.class);
        attributes.forEach(attribute -> values.put(attribute.kind(), attribute.encodedValueOf(recipe)));
        return new Snapshot(values);
    }

    /**
     * A value a person changed is theirs from now on. One they left as it was stays derived: saving
     * a recipe to fix its title is not a decision about its diet.
     */
    public void acceptPersonChanges(Snapshot before, Recipe after) {
        attributes.stream()
                .filter(attribute -> !Objects.equals(before.values().get(attribute.kind()), attribute.encodedValueOf(after)))
                .forEach(attribute -> attribute(after, attribute.kind(), null));
    }
}
