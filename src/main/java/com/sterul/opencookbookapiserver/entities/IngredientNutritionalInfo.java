package com.sterul.opencookbookapiserver.entities;

import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientNutritionalInfo {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private Ingredient ingredient;

    @ElementCollection
    @MapKeyColumn(name = "unit")
    @Column(name = "energy")
    @Builder.Default
    private Map<String, Float> energyPerUnit = new HashMap<>();

}
