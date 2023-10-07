package com.sterul.opencookbookapiserver.entities;

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
}
