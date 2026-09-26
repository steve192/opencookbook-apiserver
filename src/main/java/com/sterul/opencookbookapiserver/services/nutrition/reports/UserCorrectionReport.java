package com.sterul.opencookbookapiserver.services.nutrition.reports;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.services.nutrition.IngredientNames;
import com.sterul.opencookbookapiserver.services.nutrition.linking.LinkSuggester;
import com.sterul.opencookbookapiserver.services.nutrition.linking.NameRuleService;
import com.sterul.opencookbookapiserver.services.nutrition.linking.RuleBook;
import com.sterul.opencookbookapiserver.services.nutrition.matching.MatchCandidate;

/** Owner decisions the matcher would not make, as hints for what the catalogue should learn. */
@Service
@ConditionalOnNutritionEnabled
@Transactional(readOnly = true)
public class UserCorrectionReport {

    private final IngredientRepository ingredientRepository;
    private final CatalogueFoodRepository foodRepository;
    private final LinkSuggester suggester;
    private final NameRuleService nameRules;

    public UserCorrectionReport(IngredientRepository ingredientRepository, CatalogueFoodRepository foodRepository,
            LinkSuggester suggester, NameRuleService nameRules) {
        this.ingredientRepository = ingredientRepository;
        this.foodRepository = foodRepository;
        this.suggester = suggester;
        this.nameRules = nameRules;
    }

    /** @param food null when excluded */
    private record Decision(String name, String language, CatalogueFood food) {

        static Decision of(Ingredient ingredient) {
            return new Decision(IngredientNames.normalise(ingredient.getName()), ingredient.getOwner().getLanguage(),
                    ingredient.getCatalogueFood());
        }
    }

    /**
     * @param food        null when excluded
     * @param matcherFood null when the matcher suggests nothing
     */
    public record UserCorrection(String name, String language, CatalogueFood food, long userCount, CatalogueFood matcherFood,
            Double matcherConfidence) {
    }

    /** The corrections the most users made first. */
    public List<UserCorrection> corrections(int limit) {
        suggester.requireReady();
        var rules = nameRules.ruleBook();
        var decisions = ingredientRepository.findAllWithOwnerAndFood().stream()
                .filter(ingredient -> ingredient.getLinkSource() == Ingredient.LinkSource.USER)
                .collect(Collectors.groupingBy(Decision::of, LinkedHashMap::new, Collectors.counting()));
        var suggestions = new HashMap<Decision, MatchCandidate>();
        decisions.keySet().forEach(decision -> suggester.suggest(decision.name(), decision.language(), rules)
                .ifPresent(candidate -> suggestions.put(decision, candidate)));
        var corrections = decisions.keySet().stream()
                .filter(decision -> disagrees(decision, suggestions.get(decision), rules))
                .toList();
        var foods = foodRepository.findAllByCatalogueKeyMapped(suggestions.values().stream().map(MatchCandidate::foodKey).toList());
        return corrections.stream()
                .map(decision -> {
                    var suggestion = suggestions.get(decision);
                    return new UserCorrection(decision.name(), decision.language(), decision.food(), decisions.get(decision),
                            suggestion == null ? null : foods.get(suggestion.foodKey()),
                            suggestion == null ? null : suggestion.confidence());
                })
                .sorted(Comparator.comparingLong(UserCorrection::userCount).reversed().thenComparing(UserCorrection::name))
                .limit(limit)
                .toList();
    }

    /** An exclusion no rule backs yet counts even when the matcher suggests nothing. */
    private static boolean disagrees(Decision decision, MatchCandidate suggestion, RuleBook rules) {
        if (decision.food() == null) {
            return suggestion != null || !rules.isNotAFood(decision.name());
        }
        return suggestion == null || !suggestion.foodKey().equals(decision.food().getCatalogueKey());
    }
}
