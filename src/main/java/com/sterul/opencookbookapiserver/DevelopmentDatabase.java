package com.sterul.opencookbookapiserver;

import java.util.Arrays;
import java.util.List;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.Recipe;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.UserDetailsServiceImpl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DevelopmentDatabase {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentDatabase.class);

    @Autowired
    RecipeRepository repository;
    @Autowired
    IngredientRepository ingredientRepository;

    @Autowired
    UserDetailsServiceImpl userDetailsService;

    private User user;

    @Bean
    CommandLineRunner initDatabase() {
        return args -> {
            // saveRecipe(
            //     "Demo recipe 1",
            //     Arrays.asList("Do stuff", "Do more stuff", "Do very long stuff that takes a lot of time\nthen be happen when you are done\nok?"),
            //     Arrays.asList(createIngredientNeed("Eatable stuff", 3, "Stück"), createIngredientNeed("Healthy stuff", 3, "g"))
            // );

            this.user = userDetailsService.createUser("1", "1");
            saveRecipe(
                "Demo recipe 2",
                Arrays.asList("Do stuff", "Do more stuff", "Do very long stuff that takes a lot of time\nthen be happen when you are done\nok?", "Be done and happy", "very happy"),
                Arrays.asList(
                    createIngredientNeed("Eatable stuff", 3f, "Stück"), 
                    createIngredientNeed("Healthy stuff", 3f, "g"),
                    createIngredientNeed("Healthy stuff2", 3f, "g"),
                    createIngredientNeed("Healthy stuff3", 3f, "g"),
                    createIngredientNeed("Healthy stuff4", 3f, "g"))
            );

            // saveRecipe(
            //     "Demo recipe 3",
            //     Arrays.asList("Do stuff", "Do more stuff", "Do very long stuff that takes a lot of time\nthen be happen when you are done\nok?"),
            //     Arrays.asList(createIngredientNeed("Eatable stuff", 3, "Stück"), createIngredientNeed("Healthy stuff", 3, "g"))
            // );

        };
    }



    void saveRecipe(String title, List<String> preparationSteps, List<IngredientNeed> neededIngredients) {
        var recipe = new Recipe();
        recipe.setTitle(title);
        recipe.setPreparationSteps(preparationSteps);
        recipe.setNeededIngredients(neededIngredients);
        recipe.setOwner(this.user);
        repository.save(recipe);
    }

    IngredientNeed createIngredientNeed(String name, Float amount, String unit) {
        var ingredient = new Ingredient();
        ingredient.setName(name);
        ingredient = ingredientRepository.save(ingredient);

        var ingredientNeed = new IngredientNeed();
        ingredientNeed.setIngredient(ingredient);
        ingredientNeed.setAmount(amount);
        ingredientNeed.setUnit(unit);

        return ingredientNeed;
    }
}
