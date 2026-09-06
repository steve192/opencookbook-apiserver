package com.sterul.opencookbookapiserver.entities;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientNeed extends AuditableEntity {

    @Id
    @SequenceGenerator(name = "ingredient_need_seq", sequenceName = "ingredient_need_seq", allocationSize = 1)
    @GeneratedValue(generator = "ingredient_need_seq")
    private Long id;

    @ManyToOne
    private Ingredient ingredient;

    private Float amount;
    private String unit;

    /** One shopping list line: "500 g Flour". What is not known is left out. */
    public String describe() {
        return Stream.of(formatAmount(), unit, ingredient == null ? null : ingredient.getName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
    }

    /** 500, not 500.0. */
    private String formatAmount() {
        if (amount == null || amount.isNaN() || amount.isInfinite()) {
            return null;
        }
        return amount == Math.floor(amount) ? String.valueOf(amount.longValue()) : amount.toString();
    }
}
