package com.sterul.opencookbookapiserver.entities.nutrition;

import java.time.Instant;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * A recipe's nutrition as last computed, for ranking a cookbook without recomputing it. A cache, not a
 * source of truth: a missing row means "not computed yet", never "no nutrition".
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RecipeNutritionSummary {

    @Id
    @EqualsAndHashCode.Include
    private Long recipeId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipe_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Recipe recipe;

    /** Per serving, or for the whole recipe where it has no servings. */
    @Embedded
    @Builder.Default
    private NutrientValues perServing = new NutrientValues();

    private boolean perServingBasis;

    @Enumerated(EnumType.STRING)
    private NutritionQuality quality;

    private int warningCount;

    /** The ingredient contributing the most grams, for spreading a week over different foods. */
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private CatalogueFood mainFood;

    private Float mainFoodGramShare;

    private Instant computedAt;

    /** Which dataset the values were read from; a later release makes the row stale. */
    private String datasetLabel;
}
