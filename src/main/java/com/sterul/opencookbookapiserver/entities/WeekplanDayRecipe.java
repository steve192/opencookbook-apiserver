package com.sterul.opencookbookapiserver.entities;

import org.hibernate.annotations.UuidGenerator;
import org.springframework.lang.Nullable;

import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeekplanDayRecipe {

    @Id
    @UuidGenerator
    private String id;

    @Nullable
    @OneToOne
    private Recipe recipe;

    private boolean isSimpleRecipe;

    private String simpleRecipeText;

}
