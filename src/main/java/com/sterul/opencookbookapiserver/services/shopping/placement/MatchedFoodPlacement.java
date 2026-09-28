package com.sterul.opencookbookapiserver.services.shopping.placement;

import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.catalogue.CatalogueService;
import com.sterul.opencookbookapiserver.services.catalogue.linking.LinkSuggester;
import com.sterul.opencookbookapiserver.services.catalogue.linking.NameRuleService;

/** The food a new ingredient of this name would be linked to, for typed names and ingredients without a placed food. */
@Component
@Order(3)
class MatchedFoodPlacement implements PlacementSource {

    private final LinkSuggester suggester;
    private final NameRuleService nameRules;
    private final CatalogueService catalogue;

    MatchedFoodPlacement(LinkSuggester suggester, NameRuleService nameRules, CatalogueService catalogue) {
        this.suggester = suggester;
        this.nameRules = nameRules;
        this.catalogue = catalogue;
    }

    @Override
    public Optional<ItemPlacement> place(PlacementQuery query) {
        return suggester.suggest(query.name(), query.language(), nameRules.ruleBookFor(query.name()))
                .flatMap(candidate -> catalogue.findFood(candidate.foodKey()))
                .flatMap(ItemPlacement::of);
    }
}
