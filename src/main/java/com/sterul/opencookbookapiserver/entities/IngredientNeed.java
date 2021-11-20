package com.sterul.opencookbookapiserver.entities;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

import lombok.Getter;
import lombok.Setter;

@Entity
public class IngredientNeed {

    @Id
    @GeneratedValue
    @Getter
    @Setter
    private Long id;

    @ManyToOne
    @Getter
    @Setter
    private Ingredient ingredient;

    @Getter
    @Setter
    private Float amount;
    @Getter
    @Setter
    private String unit;


}
