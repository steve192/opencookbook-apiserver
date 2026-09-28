package com.sterul.opencookbookapiserver.services.catalogue.linking;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.services.IngredientLinker;
import com.sterul.opencookbookapiserver.services.catalogue.matching.CatalogueMatcher;

import lombok.extern.slf4j.Slf4j;

/** Links new ingredients as suggested; while the matcher is not ready they stay unmatched. */
@Component
@Slf4j
public class AutomaticIngredientLinker implements IngredientLinker {

    private final LinkSuggester suggester;
    private final NameRuleService nameRules;
    private final CatalogueFoodRepository foodRepository;
    private final Clock clock;

    public AutomaticIngredientLinker(LinkSuggester suggester, NameRuleService nameRules, CatalogueFoodRepository foodRepository,
            Clock clock) {
        this.suggester = suggester;
        this.nameRules = nameRules;
        this.foodRepository = foodRepository;
        this.clock = clock;
    }

    @Override
    public void linkNew(Ingredient ingredient) {
        if (!suggester.isReady()) {
            return;
        }
        suggester.suggest(ingredient.getName(), ingredient.getOwner().getLanguage(), nameRules.ruleBookFor(ingredient.getName()))
                .ifPresent(candidate -> foodRepository.findByCatalogueKey(candidate.foodKey())
                        .ifPresentOrElse(
                                food -> ingredient.linkAutomatically(food, (float) candidate.confidence(), CatalogueMatcher.VERSION,
                                        clock.instant(), null),
                                () -> log.warn("Matched food {} is no longer in the catalogue", candidate.foodKey())));
    }
}
