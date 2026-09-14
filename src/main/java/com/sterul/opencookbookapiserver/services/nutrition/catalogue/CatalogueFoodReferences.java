package com.sterul.opencookbookapiserver.services.nutrition.catalogue;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.repositories.CatalogueNameRuleRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;

/** What refers to a catalogue food and follows it when it is merged into another. */
@Component
public class CatalogueFoodReferences {

    private final IngredientRepository ingredientRepository;
    private final CatalogueNameRuleRepository nameRuleRepository;

    public CatalogueFoodReferences(IngredientRepository ingredientRepository, CatalogueNameRuleRepository nameRuleRepository) {
        this.ingredientRepository = ingredientRepository;
        this.nameRuleRepository = nameRuleRepository;
    }

    /** @return how many ingredients were relinked */
    public int move(CatalogueFood source, CatalogueFood target) {
        nameRuleRepository.moveToFood(source, target);
        return ingredientRepository.relink(source, target);
    }
}
