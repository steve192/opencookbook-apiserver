package com.sterul.opencookbookapiserver.configurations;

import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.repositoriespostgress.PGUserRepository;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.CookpalUser;

@Configuration
public class PGMigrator {
    @Autowired
    UserRepository userRepository;

    @Autowired
    RecipeRepository recipeRepository;
    @Autowired
    PGUserRepository pgUserRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void doSomethingAfterStartup() {
        userRepository.findAll().forEach(user -> {
            var pgUser = new CookpalUser();
            BeanUtils.copyProperties(user, pgUser);
            pgUserRepository.save(pgUser);
        });
    }
}
