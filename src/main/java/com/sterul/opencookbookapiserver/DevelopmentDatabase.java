package com.sterul.opencookbookapiserver;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DevelopmentDatabase {
    
    private static final Logger log = LoggerFactory.getLogger(DevelopmentDatabase.class);

    // @Bean
    CommandLineRunner initDatabase(RecipeRepository repository, IngredientRepository ingredientRepository) {
        return args -> {
            log.info("Creating ingredient" + ingredientRepository.save(new Ingredient()));
        };
    }
}
