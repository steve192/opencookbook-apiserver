package com.sterul.opencookbookapiserver.entities;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
public class Ingredient extends AuditableEntity {
    @Id
    @SequenceGenerator(name = "ingredient_seq", sequenceName = "ingredient_seq", allocationSize = 1)
    @GeneratedValue(generator = "ingredient_seq")
    private Long id;

    private String name;

    @ManyToOne
    private CookpalUser owner;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "ingredient_id") 
    private List<IngredientAlternativeNames> alternativeNames;

    private boolean isPublicIngredient;

    @ManyToOne
    private Ingredient aliasFor;

    private Float nutrientsEnergy;
    private Float nutrientsFat;
    private Float nutrientsSaturatedFat;
    private Float nutrientsCarbohydrates;
    private Float nutrientsSugar;
    private Float nutrientsProtein;
    private Float nutrientsSalt;

    public Ingredient getAliasFor() {
        return isPublicIngredient ? null : aliasFor;
    }

    public void setAliasFor(Ingredient aliasFor) {
        if (isPublicIngredient) {
            this.aliasFor = null;
        } else {
            this.aliasFor = aliasFor;
        }
    }

    @Override
    public String toString() {
        return id + " " + name;
    }
}
