package com.sterul.opencookbookapiserver.services;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class IngredientService {
    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private IngredientMatcher ingredientMatcher;

    public Ingredient findUserIngredientBySimilarName(String name, CookpalUser user) throws ElementNotFound {
        var ingredients = getUserPermittedIngredients(user);
        return populateNutrients(ingredientMatcher.findIngredientbySimilarName(ingredients, name));
    }

    public Ingredient findPublicIngredientBySimilarName(String name) throws ElementNotFound {
        var ingredients = getPublicIngredients();
        return populateNutrients(ingredientMatcher.findIngredientbySimilarName(ingredients, name));
    }

    private Ingredient populateNutrients(Ingredient ingredient) {
        // Public ingredients already have nutrients assigned
        if (ingredient == null || ingredient.isPublicIngredient()) {
            return ingredient;
        }
        var aliasedIngredient = ingredient.getAliasFor();
        if (aliasedIngredient == null) {
            return ingredient;
        }
        // Get nutrients from aliased ingredient
        ingredient.setNutrientsEnergy(aliasedIngredient.getNutrientsEnergy());
        ingredient.setNutrientsCarbohydrates(aliasedIngredient.getNutrientsCarbohydrates());
        ingredient.setNutrientsFat(aliasedIngredient.getNutrientsFat());
        ingredient.setNutrientsProtein(aliasedIngredient.getNutrientsProtein());
        ingredient.setNutrientsSalt(aliasedIngredient.getNutrientsSalt());
        ingredient.setNutrientsSaturatedFat(aliasedIngredient.getNutrientsSaturatedFat());
        ingredient.setNutrientsSugar(aliasedIngredient.getNutrientsSugar());

        return ingredient;
    }

    public Ingredient createPublicIngredient(Ingredient ingredient) {
        ingredient.setId(null);
        ingredient.setPublicIngredient(true);
        ingredient.setAliasFor(null);
        ingredient.getAlternativeNames().forEach(name -> name.setIngredient(ingredient));
        return ingredientRepository.save(ingredient);
    }

    public Ingredient createOrGetIngredient(Ingredient ingredient, CookpalUser user) {

        var publicIngredient = ingredientRepository.findByNameAndIsPublicIngredient(
                ingredient.getName(),
                true);

        if (publicIngredient != null) {
            return publicIngredient;
        }

        var ownIngredient = ingredientRepository.findByNameAndIsPublicIngredientAndOwner(
                ingredient.getName(),
                false,
                user);

        if (ownIngredient != null) {
            return populateNutrients(ownIngredient);
        }
        log.info("Creating new Ingredient {} for user {}", ingredient.getName(), user.getUserId());

        // Make sure a new ingredient is created
        ingredient.setId(null);
        ingredient.setPublicIngredient(false);
        ingredient.setOwner(user);

        try {
            var similarPublicIngredient = findPublicIngredientBySimilarName(ingredient.getName());
            if (similarPublicIngredient != null) {
                ingredient.setAliasFor(similarPublicIngredient);
            }
        } catch (ElementNotFound e) {
            // No public ingredient found
        }

        ingredient.getAlternativeNames().forEach(name -> name.setIngredient(ingredient));

        return populateNutrients(ingredientRepository.save(ingredient));
    }

    public boolean hasPermissionForIngredient(Long id, CookpalUser user) throws ElementNotFound {
        var ingredient = getIngredient(id);
        if (ingredient.isPublicIngredient()) {
            return true;
        }

        return ingredient.getOwner().equals(user);
    }

    public Ingredient getIngredient(Long id) throws ElementNotFound {
        var optional = ingredientRepository.findById(id);
        if (optional.isEmpty()) {
            throw new ElementNotFound();
        }
        return populateNutrients(optional.get());
    }

    public List<Ingredient> getUserPermittedIngredients(CookpalUser user) {

        var ownIngredients = ingredientRepository.findAllByIsPublicIngredientAndOwner(
                false,
                user);
        var publicIngredients = ingredientRepository.findAllByIsPublicIngredient(true);

        return Stream.concat(publicIngredients.stream(), ownIngredients.stream().map(this::populateNutrients)).toList();
    }

    public List<Ingredient> getAllIngredients() {
        return ingredientRepository.findAll().stream().map(this::populateNutrients).toList();
    }

    public List<Ingredient> getPublicIngredients() {
        return ingredientRepository.findAllByIsPublicIngredient(true);
    }

    public void deleteAllIngredientsOfUser(CookpalUser user) {
        // TODO: Check if aliased ingredients are not deleted
        log.info("Deleting all ingredients of user {}", user.getUserId());
        ingredientRepository.deleteAllByOwner(user);
    }

    public void deleteIngredient(Ingredient ingredient) {
        log.info("Deleting ingredient {}", ingredient.getId());
        ingredientRepository.delete(ingredient);
    }

    public Ingredient updateIngredient(Ingredient newIngredient) throws ElementNotFound {
        log.info("Updating ingredient {}", newIngredient.getId());
        var existingIngredient = getIngredient(newIngredient.getId());
        newIngredient.setId(existingIngredient.getId());
        newIngredient.setOwner(existingIngredient.getOwner());
        newIngredient.setPublicIngredient(existingIngredient.isPublicIngredient());
        newIngredient.getAlternativeNames().forEach(name -> name.setIngredient(newIngredient));
        if (existingIngredient.getAliasFor() != null) {
            newIngredient.setAliasFor(existingIngredient.getAliasFor());
        }

        return ingredientRepository.save(newIngredient);
    }
}
