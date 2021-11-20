package com.sterul.opencookbookapiserver.entities;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import lombok.Data;

@Entity
@Data
public class IngredientNeed {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private Ingredient ingredient;

    private Float amount;
    private String unit;


}
