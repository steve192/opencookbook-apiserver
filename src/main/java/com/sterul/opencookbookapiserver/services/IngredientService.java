package com.sterul.opencookbookapiserver.services;

import static com.intuit.fuzzymatcher.domain.ElementType.NAME;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.intuit.fuzzymatcher.component.MatchService;
import com.intuit.fuzzymatcher.domain.Document;
import com.intuit.fuzzymatcher.domain.Element;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional
@Slf4j
public class IngredientService {


    public static final String NEW_INDREGIENT_KEY = "newIngredient";
    @Autowired
    private IngredientRepository ingredientRepository;

    private Ingredient findIngredientbySimilarName(List<Ingredient> possibleIngredients, String name)
            throws ElementNotFound {
        if (possibleIngredients == null || possibleIngredients.isEmpty()) {
            throw new ElementNotFound();
        }
        var documentList = possibleIngredients.stream()
                .map(ingredient -> new Document.Builder(ingredient.getId().toString())
                        .addElement(new Element.Builder<String>()
                                .setValue(ingredient.getName())
                                .setType(NAME)
                                .createElement())
                        .createDocument())
                .collect(Collectors.toList());

        documentList.add(new Document.Builder(NEW_INDREGIENT_KEY)
                .addElement(new Element.Builder<String>()
                        .setValue(name)
                        .setType(NAME)
                        .createElement())
                .createDocument());

        MatchService matchService = new MatchService();
        var matches = matchService.applyMatchByDocId(documentList);
        if (matches.size() == 0) {
            throw new ElementNotFound();
        }
        var matchesForNamedIngredient = matches.get(NEW_INDREGIENT_KEY);

        if (matchesForNamedIngredient == null) {
            throw new ElementNotFound();
        }

        var bestMatchedIngredientId = Float.parseFloat(matchesForNamedIngredient.get(0).getMatchedWith().getKey());

        var bestMatchedIngredient = possibleIngredients.stream()
                .filter(ingredient -> ingredient.getId() == bestMatchedIngredientId)
                .findFirst();

        if (bestMatchedIngredient.isEmpty()) {
            throw new ElementNotFound();
        }
        return populateNutrients(bestMatchedIngredient.get());
    }

    public Ingredient findUserIngredientBySimilarName(String name, CookpalUser user) throws ElementNotFound {
        var ingredients = getUserPermittedIngredients(user);
        return findIngredientbySimilarName(ingredients, name);
    }

    private Ingredient findPublicIngredientBySimilarName(String name) throws ElementNotFound {
        var ingredients = getPublicIngredients();
        return findIngredientbySimilarName(ingredients, name);
    }

    private Ingredient populateNutrients(Ingredient ingredient) {
        if (ingredient == null || ingredient.isPublicIngredient()) {
            return ingredient;
        }
        var aliasedIngredient = ingredient.getAliasFor();
        if (aliasedIngredient == null) {
            return ingredient;
        }

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
        log.info("Ingredient {} created for user", ingredient.getName(), user.getUserId());

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
        if (existingIngredient.getAliasFor() != null) {
            newIngredient.setAliasFor(existingIngredient.getAliasFor());
        }

        return ingredientRepository.save(newIngredient);
    }
}
