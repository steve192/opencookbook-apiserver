package com.sterul.opencookbookapiserver.services;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IngredientService {

    @Autowired
    private IngredientRepository ingredientRepository;

    public Ingredient createOrGetIngredient(Ingredient ingredient, User user) {


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
            return ownIngredient;
        }

        // Make sure a new ingredient is created
        ingredient.setId(null);
        ingredient.setPublicIngredient(false);
        ingredient.setOwner(user);

        return ingredientRepository.save(ingredient);
    }

    public boolean hasPermissionForIngredient(Long id, User user) throws ElementNotFound {
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
        return optional.get();
    }

    public List<Ingredient> getAllIngredients() {
        return ingredientRepository.findAll();
    }

    public void deleteAllIngredientsOfUser(User user) {
        ingredientRepository.deleteAllByOwner(user);
    }
}
