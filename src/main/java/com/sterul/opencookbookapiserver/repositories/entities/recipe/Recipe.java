package com.sterul.opencookbookapiserver.repositories.entities.recipe;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.repositories.entities.AuditableEntity;
import com.sterul.opencookbookapiserver.repositories.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.repositories.entities.RecipeImage;
import com.sterul.opencookbookapiserver.repositories.entities.account.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recipe extends AuditableEntity {
    @Id
    @GeneratedValue
    private Long id;

    private String title;


    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<IngredientNeed> neededIngredients = new ArrayList<>();

    @ElementCollection
    @Column(length = 10000)
    @Builder.Default
    private List<String> preparationSteps = new ArrayList<>();

    @ManyToOne
    @JsonIgnore
    private User owner;

    @OneToMany
    @Builder.Default
    private List<RecipeImage> images = new ArrayList<>();

    private int servings;

    @ManyToMany
    @Builder.Default
    private List<RecipeGroup> recipeGroups = new ArrayList<>();

    private Long preparationTime;
    private Long totalTime;

    @Enumerated
    @org.springframework.data.relational.core.mapping.Embedded.Nullable
    private RecipeType recipeType;

    public enum RecipeType {
        VEGAN, VEGETARIAN, MEAT
    }

}
