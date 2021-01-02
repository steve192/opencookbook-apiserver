package com.sterul.opencookbookapiserver;

import com.sterul.opencookbookapiserver.entities.Recipe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DevelopmentDatabase {
    
    private static final Logger log = LoggerFactory.getLogger(DevelopmentDatabase.class);

    //@Bean
    CommandLineRunner initDatabase(RecipeRepository repository) {
        return args -> {
            log.info("Creating recipe" + repository.save(new Recipe()));
        };
    }
}
