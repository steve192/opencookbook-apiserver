package com.sterul.opencookbookapiserver.entities.recipe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sterul.opencookbookapiserver.entities.AuditableEntity;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.RecipeImage;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
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

    // Lines are recreated in list order on every save, so id order is the written order.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "recipe_id")
    @OrderBy("id")
    @Builder.Default
    private List<IngredientNeed> neededIngredients = new ArrayList<>();

    @ElementCollection
    @Column(length = 10000)
    @Builder.Default
    private List<String> preparationSteps = new ArrayList<>();

    @ManyToOne
    @JsonIgnore
    private CookpalUser owner;

    // Ordered: the first image is the recipe's title image, so the order has to survive a
    // round trip. Without an order column this is a bag and jpa gives no such guarantee.
    @OneToMany
    @JoinColumn(name = "recipe_id")
    @OrderColumn(name = "image_order")
    @Builder.Default
    private List<RecipeImage> images = new ArrayList<>();

    private int servings;

    @ManyToMany
    @Builder.Default
    private List<RecipeGroup> recipeGroups = new ArrayList<>();

    private Long preparationTime;
    private Long totalTime;

    private String recipeSource;

    @Enumerated
    private Diet recipeType;

    /** Empty while unknown. */
    @ElementCollection
    @CollectionTable(name = "recipe_meal_type", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "meal_type", length = 16)
    @Enumerated(EnumType.STRING)
    // Batched: read for a whole cookbook at once, and fetch-joining it would multiply the ingredient rows.
    @BatchSize(size = 200)
    @Builder.Default
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<MealType> mealTypes = new HashSet<>();

    /** Null for a dish, which is also what a recipe nobody marked counts as. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private DishRole dishRole;

    /** Total time, falling back to preparation time; null where neither was recorded. */
    public Long minutesNeeded() {
        return totalTime != null ? totalTime : preparationTime;
    }

    public boolean isOwnedBy(CookpalUser user) {
        return owner.getUserId().equals(user.getUserId());
    }

}
