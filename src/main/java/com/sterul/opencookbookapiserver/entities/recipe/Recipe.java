package com.sterul.opencookbookapiserver.entities.recipe;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.AuditableEntity;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
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
public class Recipe extends AuditableEntity {
    @Id
    @SequenceGenerator(name = "recipe_seq", sequenceName = "recipe_seq", allocationSize = 1)
    @GeneratedValue(generator = "recipe_seq")
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
    private CookpalUser owner;

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
