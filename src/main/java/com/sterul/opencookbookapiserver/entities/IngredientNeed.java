package com.sterul.opencookbookapiserver.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
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
    @GeneratedValue
    private Long id;

    @ManyToOne
    private Ingredient ingredient;

    private Float amount;
    private String unit;
}
