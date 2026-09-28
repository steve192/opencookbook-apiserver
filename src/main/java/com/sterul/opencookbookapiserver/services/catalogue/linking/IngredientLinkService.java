package com.sterul.opencookbookapiserver.services.catalogue.linking;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.catalogue.UnitLexicon;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDataset;

import lombok.extern.slf4j.Slf4j;

/** Links and piece weights decided by owners (their own ingredients) or administrators (any). */
@Service
@Transactional(rollbackFor = ApiException.class)
@Slf4j
public class IngredientLinkService {

    private final IngredientRepository ingredientRepository;
    private final CatalogueFoodRepository foodRepository;
    private final UnitLexicon unitLexicon;
    private final Clock clock;

    public IngredientLinkService(IngredientRepository ingredientRepository, CatalogueFoodRepository foodRepository,
            UnitLexicon unitLexicon, Clock clock) {
        this.ingredientRepository = ingredientRepository;
        this.foodRepository = foodRepository;
        this.unitLexicon = unitLexicon;
        this.clock = clock;
    }

    public Ingredient link(Long ingredientId, CookpalUser owner, Long catalogueFoodId) {
        var ingredient = ownIngredient(ingredientId, owner);
        var food = food(catalogueFoodId);
        log.info("User {} links ingredient {} to catalogue food {}", owner.getUserId(), ingredientId, food.getCatalogueKey());
        ingredient.linkByOwner(food, clock.instant());
        return ingredient;
    }

    public Ingredient exclude(Long ingredientId, CookpalUser owner) {
        var ingredient = ownIngredient(ingredientId, owner);
        log.info("User {} excludes ingredient {} from nutrition", owner.getUserId(), ingredientId);
        ingredient.excludeByOwner(clock.instant());
        return ingredient;
    }

    /** @param unitWord as recipes write it ("Stück", "Dose"); blank for pieces */
    public Ingredient setOwnPortion(Long ingredientId, CookpalUser owner, String unitWord, float grams) {
        var ingredient = ownIngredient(ingredientId, owner);
        var unit = countUnit(unitWord);
        log.info("User {} weighs one {} of ingredient {} as {} g", owner.getUserId(), unit.key(), ingredientId, grams);
        ingredient.getPortionOverrides().put(unit.key(), grams);
        return ingredient;
    }

    public Ingredient removeOwnPortion(Long ingredientId, CookpalUser owner, String unitWord) {
        var ingredient = ownIngredient(ingredientId, owner);
        ingredient.getPortionOverrides().remove(countUnit(unitWord).key());
        return ingredient;
    }

    public Ingredient linkByAdmin(Long ingredientId, Long catalogueFoodId) {
        var ingredient = ingredient(ingredientId);
        var food = food(catalogueFoodId);
        log.info("Admin: Linking ingredient {} to catalogue food {}", ingredientId, food.getCatalogueKey());
        ingredient.linkByAdmin(food, clock.instant());
        return ingredient;
    }

    public Ingredient excludeByAdmin(Long ingredientId) {
        var ingredient = ingredient(ingredientId);
        log.info("Admin: Excluding ingredient {} from nutrition", ingredientId);
        ingredient.excludeByAdmin(clock.instant());
        return ingredient;
    }

    private CatalogueDataset.Unit countUnit(String unitWord) {
        return unitLexicon.resolveLineUnit(unitWord)
                .filter(unit -> unit.kind() == CatalogueDataset.UnitKind.COUNT)
                .orElseThrow(() -> new ApiException(ApiErrorCode.VALIDATION_FAILED,
                        "Only a unit that counts pieces has a weight per piece: '" + unitWord + "'"));
    }

    private Ingredient ownIngredient(Long id, CookpalUser owner) {
        return ingredientRepository.findByIdAndOwner(id, owner).orElseThrow(ElementNotFound::new);
    }

    private Ingredient ingredient(Long id) {
        return ingredientRepository.findById(id).orElseThrow(ElementNotFound::new);
    }

    private CatalogueFood food(Long id) {
        return foodRepository.findById(id).orElseThrow(ElementNotFound::new);
    }
}
