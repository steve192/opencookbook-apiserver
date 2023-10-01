package com.sterul.opencookbookapiserver.repositoriespostgress.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

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
