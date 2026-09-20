package com.sterul.opencookbookapiserver.entities.planning;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Something the cook has and wants used up. The amount is what makes it a budget rather than a
 * preference: the plan should use it up, and then stop favouring it.
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PantryItem {

    /** One of the owner's own ingredients, matched through the catalogue like a wanted one. */
    @Column(nullable = false)
    private Long ingredientId;

    /** Null when the cook named the ingredient without saying how much. */
    private Float amount;

    @Column(length = 32)
    private String unit;
}
