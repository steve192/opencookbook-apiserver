package com.sterul.opencookbookapiserver.services;

import java.util.List;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class IngredientService {

    @Autowired
    private IngredientRepository ingredientRepository;

    public Ingredient createOrGetIngredient(Ingredient ingredient) {
        var existingIngredient = ingredientRepository.findByName(ingredient.getName());
        if (existingIngredient != null) {
            return existingIngredient;
        }

        // Make sure a new ingredient is created
        ingredient.setId(null);
        return ingredientRepository.save(ingredient);
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

    public Ingredient findIngredientByName(String name) {
        return ingredientRepository.findByName(name);
    }

}
