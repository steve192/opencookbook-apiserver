package com.sterul.opencookbookapiserver.entities.recipe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.ChangeInformationEmbeddable;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Embedded;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recipe {
    @Id
    @GeneratedValue
    private Long id;

    private String title;


    @javax.persistence.Embedded
    private ChangeInformationEmbeddable changeInformationEmbeddable;

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
    @Embedded.Nullable
    private RecipeType recipeType;

    public enum RecipeType {
        VEGAN, VEGETARIAN, MEAT
    }

}
