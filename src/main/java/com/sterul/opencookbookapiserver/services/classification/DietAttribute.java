package com.sterul.opencookbookapiserver.services.classification;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.recipe.ClassificationKind;
import com.sterul.opencookbookapiserver.entities.recipe.Diet;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;

@Component
public class DietAttribute implements ClassifiedAttribute<Diet> {

    @Override
    public ClassificationKind kind() {
        return ClassificationKind.DIET;
    }

    @Override
    public Diet valueOf(Recipe recipe) {
        return recipe.getRecipeType();
    }

    @Override
    public void write(Recipe recipe, Diet value) {
        recipe.setRecipeType(value);
    }

    @Override
    public String encode(Diet value) {
        return value.name();
    }

    @Override
    public Diet decode(String encoded) {
        return Diet.valueOf(encoded);
    }
}
