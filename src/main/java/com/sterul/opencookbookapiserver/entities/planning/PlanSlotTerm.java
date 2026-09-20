package com.sterul.opencookbookapiserver.entities.planning;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One term of the score that placed a recipe in a slot, kept so the draft can say why. */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanSlotTerm {

    @Column(nullable = false, length = 32)
    private String term;

    @Column(name = "term_value", nullable = false)
    private double value;
}
