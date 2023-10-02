package com.sterul.opencookbookapiserver.configurations;

import java.util.HashMap;
import java.util.Map;

import javax.transaction.Transactional;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.sterul.opencookbookapiserver.repositories.ActivationLinkRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.PasswordResetLinkRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeGroupRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeImageRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.RefreshTokenRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.repositories.WeekplanDayRepository;
import com.sterul.opencookbookapiserver.repositories.entities.account.User;
import com.sterul.opencookbookapiserver.repositoriespostgress.PGActivationLinkRepository;
import com.sterul.opencookbookapiserver.repositoriespostgress.PGIngredientRepository;
import com.sterul.opencookbookapiserver.repositoriespostgress.PGPasswordResetLinkRepository;
import com.sterul.opencookbookapiserver.repositoriespostgress.PGRecipeGroupRepository;
import com.sterul.opencookbookapiserver.repositoriespostgress.PGRecipeImageRepository;
import com.sterul.opencookbookapiserver.repositoriespostgress.PGRecipeRepository;
import com.sterul.opencookbookapiserver.repositoriespostgress.PGRefreshTokenRepository;
import com.sterul.opencookbookapiserver.repositoriespostgress.PGUserRepository;
import com.sterul.opencookbookapiserver.repositoriespostgress.PGWeekplanDayRepository;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.RecipeImage;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.recipe.Recipe.RecipeType;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class PGMigrator {
    @Autowired
    UserRepository userRepository;
    @Autowired
    PGUserRepository pgUserRepository;

    @Autowired
    RecipeRepository recipeRepository;
    @Autowired
    PGRecipeRepository pgRecipeRepository;

    @Autowired
    ActivationLinkRepository activationLinkRepository;
    @Autowired
    PGActivationLinkRepository pgActivationLinkRepository;

    @Autowired
    IngredientRepository ingredientRepository;
    @Autowired
    PGIngredientRepository pgIngredientRepository;

    @Autowired
    PasswordResetLinkRepository passwordResetLinkRepository;
    @Autowired
    PGPasswordResetLinkRepository pgPasswordResetLinkRepository;

    @Autowired
    RecipeGroupRepository recipeGroupRepository;
    @Autowired
    PGRecipeGroupRepository pgRecipeGroupRepository;

    @Autowired
    RecipeImageRepository recipeImageRepository;
    @Autowired
    PGRecipeImageRepository pgRecipeImageRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;
    @Autowired
    PGRefreshTokenRepository pgRefreshTokenRepository;

    @Autowired
    WeekplanDayRepository weekplanDayRepository;
    @Autowired
    PGWeekplanDayRepository pgWeekplanDayRepository;

    Map<Long, Long> userMap = new HashMap();
    Map<Long, Long> recipeGroupMap = new HashMap();
    Map<Long, Long> ingredientMap = new HashMap();

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void doSomethingAfterStartup() {
        userRepository.findAll().forEach(user -> {
            var pgUser = new CookpalUser();
            BeanUtils.copyProperties(user, pgUser);
            pgUser = pgUserRepository.save(pgUser);
            userMap.put(user.getUserId(), pgUser.getUserId());
        });

        recipeRepository.findAll().forEach(recipe -> {
            var pgRecipe = new com.sterul.opencookbookapiserver.repositoriespostgress.entities.recipe.Recipe();
            BeanUtils.copyProperties(recipe, pgRecipe);
            pgRecipe.setPreparationSteps(recipe.getPreparationSteps().stream().toList());

            pgRecipe.setImages(recipe.getImages().stream().map(image -> {
                var pgImage = new RecipeImage();
                BeanUtils.copyProperties(image, pgImage);
                pgImage.setOwner(cu(image.getOwner()));
                return pgRecipeImageRepository.save(pgImage);
            }).toList());

            pgRecipe.setNeededIngredients(recipe.getNeededIngredients().stream().map(ineed -> {
                var pgNeed = new com.sterul.opencookbookapiserver.repositoriespostgress.entities.IngredientNeed();
                BeanUtils.copyProperties(ineed, pgNeed);
                var ingredient = ineed.getIngredient();

                var pgIngredient = new com.sterul.opencookbookapiserver.repositoriespostgress.entities.Ingredient();
                BeanUtils.copyProperties(ingredient, pgIngredient);
                pgIngredient.setOwner(cu(ingredient.getOwner()));

                if (ingredientMap.containsKey(ingredient.getId())) {
                    pgIngredient.setId(ingredientMap.get(ingredient.getId()));
                }

                pgIngredient = pgIngredientRepository.save(pgIngredient);
                ingredientMap.putIfAbsent(ingredient.getId(), pgIngredient.getId());

                pgNeed.setIngredient(pgIngredient);

                return pgNeed;
            }).toList());

            pgRecipe.setOwner(cu(recipe.getOwner()));

            pgRecipe.setRecipeGroups(recipe.getRecipeGroups().stream().map(group -> {
                var pgGroup = new com.sterul.opencookbookapiserver.repositoriespostgress.entities.recipe.RecipeGroup();
                BeanUtils.copyProperties(group, pgGroup);
                pgGroup.setOwner(cu(group.getOwner()));

                if (recipeGroupMap.containsKey(group.getId())) {
                    pgGroup.setId(recipeGroupMap.get(group.getId()));
                }

                pgGroup = pgRecipeGroupRepository.save(pgGroup);
                recipeGroupMap.putIfAbsent(group.getId(), pgGroup.getId());
                return pgGroup;
            }).toList());

            if (recipe.getRecipeType() != null) {
                pgRecipe.setRecipeType(RecipeType.valueOf(recipe.getRecipeType().name()));
            }

            pgRecipeRepository.save(pgRecipe);
        });

        activationLinkRepository.findAll().forEach(al -> {
            var pgAl = new com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.ActivationLink();
            BeanUtils.copyProperties(al, pgAl);

            pgAl.setUser(cu(al.getUser()));

            pgActivationLinkRepository.save(pgAl);
        });

        log.info("Migration done");
    }

    private CookpalUser cu(User user) {
        var pgUser = new CookpalUser();
        BeanUtils.copyProperties(user, pgUser);
        pgUser.setUserId(userMap.get(user.getUserId()));
        return pgUser;
    }

}
