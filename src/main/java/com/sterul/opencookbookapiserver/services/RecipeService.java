package com.sterul.opencookbookapiserver.services;

import com.intuit.fuzzymatcher.component.MatchService;
import com.intuit.fuzzymatcher.domain.Document;
import com.intuit.fuzzymatcher.domain.Element;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import static com.intuit.fuzzymatcher.domain.ElementType.NAME;

@Service
public class RecipeService {

    public static final String SEARCH_DOCUMENT = "searchDocument";
    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    @Lazy
    private RecipeGroupService recipeGroupService;

    @Autowired
    private WeekplanService weekplanService;

    public Recipe createNewRecipe(Recipe newRecipe) {
        createMissingIngredients(newRecipe, newRecipe.getOwner());
        createMissingRecipeGroup(newRecipe);

        return recipeRepository.save(newRecipe);
    }

    private void createMissingIngredients(Recipe recipe, User user) {
        for (var ingredientNeed : recipe.getNeededIngredients()) {
            var ingredient = ingredientNeed.getIngredient();
            if (ingredient.getId() == null) {
                // Convenience api which creates ingredients
                ingredientNeed.setIngredient(ingredientService.createOrGetIngredient(ingredient, user));
            }
        }
    }

    private void createMissingRecipeGroup(Recipe recipe) {
        for (var recipeGroup : recipe.getRecipeGroups()) {
            if (recipeGroup.getId() == null) {
                // Convenience api which creates recipe groups
                recipeGroup.setOwner(recipe.getOwner());
                var createdRecipeGroup = recipeGroupService.createRecipeGroup(recipeGroup);
                recipeGroup.setId(createdRecipeGroup.getId());
            }
        }
    }

    public List<Recipe> getRecipesByOwner(User owner) {
        return recipeRepository.findByOwner(owner);
    }

    public void deleteRecipe(Long id) {
        var weekplanDays = weekplanService.getWeekplanDaysByRecipe(id);
        for (var weekplanDay : weekplanDays) {
            var iterator = weekplanDay.getRecipes().iterator();
            while (iterator.hasNext()) {
                var recipe = iterator.next();
                if (recipe.getRecipe() != null && recipe.getRecipe().getId().equals(id)) {
                    iterator.remove();
                }
            }
            weekplanService.updateWeekplanDay(weekplanDay);
        }
        recipeRepository.deleteById(id);
    }

    public boolean hasAccessPermissionToRecipe(Long recipeId, User user) throws ElementNotFound {
        var recipe = recipeRepository.findById(recipeId);
        if (!recipe.isPresent()) {
            throw new ElementNotFound();
        }
        return recipe.get().getOwner().getUserId().equals(user.getUserId());
    }

    public List<Recipe> getRecipesByRecipeGroup(RecipeGroup recipeGroup) {
        return recipeRepository.findByRecipeGroups(recipeGroup);
    }

    public Recipe updateSingleRecipe(Recipe recipeUpdate) {
        return recipeRepository.findById(recipeUpdate.getId()).map(existingRecipe -> {
            recipeUpdate.setId(existingRecipe.getId());
            recipeUpdate.setOwner(existingRecipe.getOwner());

            createMissingIngredients(recipeUpdate, existingRecipe.getOwner());
            createMissingRecipeGroup(recipeUpdate);

            return recipeRepository.save(recipeUpdate);
        }).orElseThrow();
    }

    public Recipe getRecipeById(Long id) throws ElementNotFound {
        var recipe = recipeRepository.findById(id);
        if (!recipe.isPresent()) {
            throw new ElementNotFound();
        }
        return recipe.get();
    }

    public List<Recipe> searchUserRecipes(User user, String searchString, List<Recipe.RecipeType> categories) {
        if ((searchString == null || searchString.equals("")) && (categories == null || categories.isEmpty())) {
            return getRecipesByOwner(user);
        }
        if (searchString == null || searchString.equals("")) {
            return recipeRepository.findByOwnerAndRecipeTypeIn(user, categories);
        }

        return searchByStringAndType(user, searchString, categories);
    }

    private List<Recipe> searchByStringAndType(User user, String searchString, List<Recipe.RecipeType> categories) {
        List<Recipe> allRecipes;
        if (categories == null || categories.isEmpty()) {
            allRecipes = recipeRepository.findByOwner(user);
        } else {
            allRecipes = recipeRepository.findByOwnerAndRecipeTypeIn(user, categories);
        }

        var documents = allRecipes.stream().map(recipe ->
                new Document.Builder(recipe.getId().toString())
                        .addElement(new Element.Builder<String>()
                                .setValue(recipe.getTitle())
                                .setType(NAME)
                                .createElement())
                        .createDocument()).toList();

        MatchService matchService = new MatchService();

        var newDocument = new Document.Builder(SEARCH_DOCUMENT)
                .addElement(new Element.Builder<String>()
                        .setValue(searchString)
                        .setType(NAME)
                        .setThreshold(0.01)
                        .createElement())
                .setThreshold(0.01)
                .createDocument();

        var matches = matchService.applyMatchByDocId(newDocument, documents);

        if (matches.size() == 0 || matches.get(SEARCH_DOCUMENT) == null) {
            // None found
            return Arrays.asList();
        }

        var results = matches.get(SEARCH_DOCUMENT);
        return results.stream().map(result ->
                        allRecipes.stream()
                                .filter(recipe ->
                                        recipe.getId().equals(Long.valueOf(result.getMatchedWith().getKey())))
                                .findFirst()
                                .get())
                .toList();
    }
}
