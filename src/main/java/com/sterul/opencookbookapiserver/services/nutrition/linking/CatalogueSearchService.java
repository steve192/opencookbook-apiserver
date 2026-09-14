package com.sterul.opencookbookapiserver.services.nutrition.linking;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueMatcher;
import com.sterul.opencookbookapiserver.services.nutrition.matching.MatchCandidate;

@Service
@ConditionalOnNutritionEnabled
@Transactional(readOnly = true)
public class CatalogueSearchService {

    private final CatalogueMatcher matcher;
    private final CatalogueFoodRepository foodRepository;

    public CatalogueSearchService(CatalogueMatcher matcher, CatalogueFoodRepository foodRepository) {
        this.matcher = matcher;
        this.foodRepository = foodRepository;
    }

    public record Found(CatalogueFood food, double confidence) {
    }

    /** Most likely first; empty while the matcher is not ready. */
    public List<Found> search(String text, String language, int limit) {
        var candidates = matcher.rank(text, language, limit);
        var foods = foodRepository.findAllByCatalogueKeyMapped(candidates.stream().map(MatchCandidate::foodKey).toList());
        return candidates.stream()
                .filter(candidate -> foods.containsKey(candidate.foodKey()))
                .map(candidate -> new Found(foods.get(candidate.foodKey()), candidate.confidence()))
                .toList();
    }

    /** For ingredient name autocomplete. */
    public List<String> names(String language) {
        return foodRepository.findNamesIn(language);
    }
}
