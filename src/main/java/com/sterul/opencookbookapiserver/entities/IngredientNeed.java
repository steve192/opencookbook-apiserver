package com.sterul.opencookbookapiserver.entities;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

@Entity
public class IngredientNeed {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private Ingredient ingredient;

    private Float amount;
    private String unit;

    public Float getAmount() {
        return amount;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void setAmount(Float amount) {
        this.amount = amount;
    }

    public Ingredient getIngredient() {
        return ingredient;
    }

    public void setIngredient(Ingredient ingredient) {
        this.ingredient = ingredient;
    }

}
