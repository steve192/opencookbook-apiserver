package com.sterul.opencookbookapiserver.entities.nutrition;

import java.time.Instant;

import com.sterul.opencookbookapiserver.entities.Ingredient;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** An ingredient's link as previewed: apply skips it if changed since, revert restores it. */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientRelinkMember {

    private Long ingredientId;
    private Instant previewedLastChange;
    private Long oldFoodId;

    @Enumerated(EnumType.STRING)
    private Ingredient.LinkSource oldLinkSource;

    private Float oldLinkConfidence;
    private Integer oldLinkMatcherVersion;
    private Instant oldLinkedAt;
    private boolean applied;
}
