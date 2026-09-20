package com.sterul.opencookbookapiserver.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.projections.OwnerCount;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional(rollbackFor = ApiException.class)
@Slf4j
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final Optional<IngredientLinker> linker;

    public IngredientService(IngredientRepository ingredientRepository, Optional<IngredientLinker> linker) {
        this.ingredientRepository = ingredientRepository;
        this.linker = linker;
    }

    /** By name only; the template's id is ignored. A new ingredient is linked automatically. */
    public Ingredient createOrGetIngredient(Ingredient template, CookpalUser owner) {
        var ingredient = existingOrUnsaved(template.getName(), template.getAdditionalInfo(), owner);
        if (ingredient.getId() != null) {
            return ingredient;
        }
        log.info("Creating ingredient {} for user {}", ingredient.getName(), owner.getUserId());
        return ingredientRepository.save(ingredient);
    }

    /** The owner's ingredient of this name, or a new one not yet saved, linked as it would be when it is. */
    public Ingredient existingOrUnsaved(String name, String additionalInfo, CookpalUser owner) {
        var tidy = name.trim();
        return ingredientRepository.findByNameAndOwner(tidy, owner).orElseGet(() -> {
            var ingredient = Ingredient.builder().name(tidy).additionalInfo(additionalInfo).owner(owner).build();
            linker.ifPresent(automatic -> automatic.linkNew(ingredient));
            return ingredient;
        });
    }

    /** Somebody else's ingredient is not found. */
    public Ingredient getOwnIngredient(Long id, CookpalUser owner) throws ElementNotFound {
        return ingredientRepository.findByIdAndOwner(id, owner).orElseThrow(ElementNotFound::new);
    }

    public List<Ingredient> getIngredientsOfUser(CookpalUser owner) {
        return ingredientRepository.findAllByOwner(owner);
    }

    public Ingredient getIngredient(Long id) throws ElementNotFound {
        return ingredientRepository.findById(id).orElseThrow(ElementNotFound::new);
    }

    public List<Ingredient> getAllIngredients() {
        return ingredientRepository.findAll();
    }

    public Map<Long, Long> countIngredientsPerOwner() {
        return OwnerCount.asMap(ingredientRepository.countGroupedByOwner());
    }

    public void deleteAllIngredientsOfUser(CookpalUser user) {
        log.info("Deleting all ingredients of user {}", user.getUserId());
        ingredientRepository.deleteAllByOwner(user);
    }

    public void deleteIngredient(Ingredient ingredient) throws ApiException {
        if (ingredientRepository.isUsedByARecipe(ingredient)) {
            throw new ApiException(ApiErrorCode.CONFLICT, "Ingredient " + ingredient.getId() + " is used by a recipe");
        }
        log.info("Deleting ingredient {}", ingredient.getId());
        ingredientRepository.delete(ingredient);
    }
}
