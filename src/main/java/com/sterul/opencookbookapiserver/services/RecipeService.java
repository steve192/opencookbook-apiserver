package com.sterul.opencookbookapiserver.services;

import static com.intuit.fuzzymatcher.domain.ElementType.NAME;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.intuit.fuzzymatcher.component.MatchService;
import com.intuit.fuzzymatcher.domain.Document;
import com.intuit.fuzzymatcher.domain.Element;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeGroup;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.repositories.projections.OwnerCount;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.sharing.ShareService;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
public class RecipeService {

    public static final String SEARCH_DOCUMENT = "searchDocument";

    private final RecipeReferenceResolver recipeReferenceResolver;
    private final RecipeRepository recipeRepository;
    private final RecipeImageService recipeImageService;
    private final WeekplanService weekplanService;
    private final ShareService shareService;

    public RecipeService(RecipeReferenceResolver recipeReferenceResolver, RecipeRepository recipeRepository,
            RecipeImageService recipeImageService,
            WeekplanService weekplanService,
            // ShareService depends on RecipeService in turn: sharing is about recipes, and the
            // only thing pointing the other way is withdrawing a share when its recipe goes.
            @Lazy ShareService shareService) {
        this.recipeReferenceResolver = recipeReferenceResolver;
        this.recipeRepository = recipeRepository;
        this.recipeImageService = recipeImageService;
        this.weekplanService = weekplanService;
        this.shareService = shareService;
    }

    public Recipe createNewRecipe(Recipe newRecipe) throws ElementNotFound {
        log.info("Creating new recipe {} for user {}", newRecipe.getTitle(), newRecipe.getOwner());
        recipeReferenceResolver.resolve(newRecipe, newRecipe.getOwner());

        return recipeRepository.save(newRecipe);
    }

    public List<Recipe> getRecipesByOwner(CookpalUser owner) {
        return recipeRepository.findByOwner(owner);
    }

    public Map<Long, Long> countRecipesPerOwner() {
        return OwnerCount.asMap(recipeRepository.countGroupedByOwner());
    }

    public void deleteRecipe(Long id) throws ElementNotFound {
        deleteRecipe(getRecipeById(id));
    }

    public void deleteRecipe(Recipe recipe) {
        var id = recipe.getId();
        log.info("Deleting recipe {}", id);

        shareService.revokeAllSharesOfRecipe(recipe);

        var weekplanDays = weekplanService.getWeekplanDaysByRecipe(id);
        for (var weekplanDay : weekplanDays) {
            var iterator = weekplanDay.getRecipes().iterator();
            while (iterator.hasNext()) {
                var planned = iterator.next();
                if (planned.getRecipe() != null && planned.getRecipe().getId().equals(id)) {
                    iterator.remove();
                }
            }
            weekplanService.updateWeekplanDay(weekplanDay);
        }
        recipe.getImages().forEach(image -> {
            try {
                recipeImageService.deleteImage(image.getUuid());
            } catch (IOException e) {
                log.error("Error deleting image {} while deleting recipe {}", image.getUuid(), id);
            }
        });
        recipeRepository.deleteById(id);
    }

    public boolean hasAccessPermissionToRecipe(Long recipeId, CookpalUser user) throws ElementNotFound {
        var recipe = recipeRepository.findById(recipeId);
        if (!recipe.isPresent()) {
            throw new ElementNotFound();
        }
        return recipe.get().getOwner().getUserId().equals(user.getUserId());
    }

    public void removeRecipeGroupFromRecipes(RecipeGroup recipeGroup) {
        for (var recipe : recipeRepository.findByRecipeGroups(recipeGroup)) {
            log.info("Removing recipe group {} from recipe {}", recipeGroup.getId(), recipe.getId());
            recipe.getRecipeGroups().removeIf(group -> group.getId().equals(recipeGroup.getId()));
            recipeRepository.save(recipe);
        }
    }

    public Recipe updateSingleRecipe(Recipe recipeUpdate) throws ElementNotFound {
        var existingRecipe = getRecipeById(recipeUpdate.getId());
        log.info("Updating recipe {} of user {}", existingRecipe.getId(), existingRecipe.getOwner());
        recipeUpdate.setOwner(existingRecipe.getOwner());
        recipeUpdate.setRecipeSource(existingRecipe.getRecipeSource());

        recipeReferenceResolver.resolve(recipeUpdate, existingRecipe.getOwner());

        return recipeRepository.save(recipeUpdate);
    }

    /** The details an operator may correct; ingredients, images and groups are untouched. */
    public record RecipeDetails(String title, int servings, Long preparationTime, Long totalTime,
            Recipe.RecipeType recipeType, List<String> preparationSteps) {

        public RecipeDetails {
            preparationSteps = preparationSteps == null ? List.of() : List.copyOf(preparationSteps);
        }
    }

    /** Every detail given replaces the one that was there, so leaving one out clears it. */
    public Recipe updateRecipeDetails(Long id, RecipeDetails details) throws ElementNotFound {
        log.info("Updating the details of recipe {}", id);
        var recipe = getRecipeById(id);

        recipe.setTitle(details.title());
        recipe.setServings(details.servings());
        recipe.setPreparationTime(details.preparationTime());
        recipe.setTotalTime(details.totalTime());
        recipe.setRecipeType(details.recipeType());
        recipe.setPreparationSteps(new ArrayList<>(details.preparationSteps()));

        return recipeRepository.save(recipe);
    }

    public Recipe getRecipeById(Long id) throws ElementNotFound {
        var recipe = recipeRepository.findById(id);
        if (!recipe.isPresent()) {
            throw new ElementNotFound();
        }
        return recipe.get();
    }

    /**
     * A recipe, held against concurrent writers until the surrounding transaction ends. For
     * callers that decide something by reading a recipe and then writing it - "share this unless
     * it is already shared" - which two requests arriving together would both answer "not yet".
     */
    public Recipe getRecipeForUpdate(Long id) throws ElementNotFound {
        return recipeRepository.findForUpdateById(id).orElseThrow(ElementNotFound::new);
    }

    public List<Recipe> searchUserRecipes(CookpalUser user, String searchString, List<Recipe.RecipeType> categories) {
        if ((searchString == null || searchString.equals("")) && (categories == null || categories.isEmpty())) {
            return getRecipesByOwner(user);
        }
        if (searchString == null || searchString.equals("")) {
            return recipeRepository.findByOwnerAndRecipeTypeIn(user, categories);
        }

        return searchByStringAndType(user, searchString, categories);
    }

    private List<Recipe> searchByStringAndType(CookpalUser user, String searchString, List<Recipe.RecipeType> categories) {
        List<Recipe> allRecipes;
        if (categories == null || categories.isEmpty()) {
            allRecipes = recipeRepository.findByOwner(user);
        } else {
            allRecipes = recipeRepository.findByOwnerAndRecipeTypeIn(user, categories);
        }

        var documents = allRecipes.stream().map(recipe -> new Document.Builder(recipe.getId().toString())
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
        return results.stream().map(result -> allRecipes.stream()
                .filter(recipe -> recipe.getId().equals(Long.valueOf(result.getMatchedWith().getKey())))
                .findFirst()
                .get())
                .toList();
    }

    public List<Recipe> getAllRecipes() {
        return recipeRepository.findAll();
    }

    public long getRecipeCount() {
        return recipeRepository.count();
    }
}
