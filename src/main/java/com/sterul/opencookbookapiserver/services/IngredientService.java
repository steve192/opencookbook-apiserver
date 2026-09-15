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
        var name = template.getName().trim();
        return ingredientRepository.findByNameAndOwner(name, owner).orElseGet(() -> {
            log.info("Creating ingredient {} for user {}", name, owner.getUserId());
            var ingredient = Ingredient.builder()
                    .name(name)
                    .additionalInfo(template.getAdditionalInfo())
                    .owner(owner)
                    .build();
            linker.ifPresent(automatic -> automatic.linkNew(ingredient));
            return ingredientRepository.save(ingredient);
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
