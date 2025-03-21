package com.sterul.opencookbookapiserver.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
public class IngredientAlternativeNames extends AuditableEntity{
    @Id
    @SequenceGenerator(name = "ingredient_alternativenames_seq", sequenceName = "ingredient_alternativenames_seq", allocationSize = 1)
    @GeneratedValue(generator = "ingredient_alternativenames_seq")
    private Long id;

    @Id
    @ManyToOne
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Id
    private String languageIsoCode;

    private String alternativeName;
}
