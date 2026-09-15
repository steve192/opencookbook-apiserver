package com.sterul.opencookbookapiserver.services.nutrition.matching;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDatasetReader;

import lombok.extern.slf4j.Slf4j;

/** Matches typed ingredient names to catalogue foods with a calibrated confidence. The index is swapped atomically. */
@Component
@ConditionalOnNutritionEnabled
@Slf4j
public class CatalogueMatcher {

    /** Bump on every change to matching logic or weights; stored with automatic links. */
    public static final int VERSION = 2;

    private static final int CANDIDATE_NAMES = 30;
    private static final String UNPREPARED = "RAW";

    private final NutritionDataset.Lexicons lexicons;
    private final List<String> languages;
    private final MatcherWeights weights;
    private final AtomicReference<CatalogueIndex> index = new AtomicReference<>();

    @Autowired
    public CatalogueMatcher(NutritionDatasetReader reader) {
        this(reader, MatcherWeights.shipped(reader));
    }

    /** For calibration with other weights. */
    public CatalogueMatcher(NutritionDatasetReader reader, MatcherWeights weights) {
        this.lexicons = reader.lexicons();
        this.languages = reader.manifest().languages();
        this.weights = weights;
    }

    public void rebuild(Collection<MatchableFood> foods) {
        var started = System.nanoTime();
        index.set(CatalogueIndex.build(foods, lexicons, languages));
        log.info("Catalogue matcher index built over {} foods in {} ms", foods.size(), (System.nanoTime() - started) / 1_000_000);
    }

    public boolean isReady() {
        return index.get() != null;
    }

    public void requireReady() throws ApiException {
        if (!isReady()) {
            throw new ApiException(ApiErrorCode.CONFLICT, "The matcher is not ready: the catalogue is still being imported");
        }
    }

    /** @param language likely language of the name; null if unknown */
    public Optional<MatchCandidate> match(String typedName, String language) {
        return rank(typedName, language, 1).stream().filter(candidate -> candidate.band().links()).findFirst();
    }

    /** Most likely first; empty while not ready. */
    public List<MatchCandidate> rank(String typedName, String language, int limit) {
        var current = index.get();
        if (current == null || typedName == null) {
            return List.of();
        }
        var typed = current.analyzer().typed(typedName);
        if (typed.words().isEmpty()) {
            return List.of();
        }

        // Best-scoring name per food.
        var byFood = new LinkedHashMap<String, Scored>();
        for (var entry : current.search(typed, CANDIDATE_NAMES)) {
            var scored = score(typed, language, entry, current);
            byFood.merge(scored.food().key(), scored, (first, second) -> first.logOdds() >= second.logOdds() ? first : second);
        }
        var ranked = byFood.values().stream().sorted(Comparator.comparingDouble(Scored::logOdds).reversed()).toList();
        return withMargins(ranked).stream().limit(limit).toList();
    }

    private Scored score(AnalyzedName typed, String language, CatalogueIndex.Entry entry, CatalogueIndex current) {
        var food = stateFitting(typed.states(), entry.food(), current);
        var comparison = NameComparison.compare(typed, entry.name());
        var wanted = withoutUnprepared(typed.states());
        var has = withoutUnprepared(food.states());
        var features = new MatchFeatures(
                comparison.queryCoverage(),
                comparison.candidateCoverage(),
                comparison.exact() ? 1 : 0,
                comparison.fuzzyShare(),
                comparison.unexplainedWords(),
                comparison.conflictingFoodWords(),
                comparison.otherFoodWords(),
                difference(wanted, has),
                difference(has, wanted),
                language == null || language.equals(entry.language()) ? 0 : 1,
                0);
        return new Scored(food, entry.name().name(), features, weights.logOddsWithoutMargin(features));
    }

    /** The base or variant whose states fit best; the base on a tie. */
    private static MatchableFood stateFitting(Set<String> typedStates, MatchableFood base, CatalogueIndex current) {
        var wanted = withoutUnprepared(typedStates);
        var options = new ArrayList<MatchableFood>();
        options.add(base);
        options.addAll(current.variantsOf(base));
        return options.stream()
                .min(Comparator.comparingInt((MatchableFood option) -> {
                    var has = withoutUnprepared(option.states());
                    // A missing state changes the food more than an unasked one.
                    return 2 * difference(wanted, has) + difference(has, wanted);
                }))
                .orElse(base);
    }

    private List<MatchCandidate> withMargins(List<Scored> ranked) {
        var candidates = new ArrayList<MatchCandidate>();
        for (var position = 0; position < ranked.size(); position++) {
            var scored = ranked.get(position);
            var margin = MatcherWeights.logistic(scored.logOdds()) - rivalProbability(ranked, position);
            var features = scored.features().withMargin(margin);
            candidates.add(new MatchCandidate(scored.food().key(), scored.matchedName(), weights.confidence(features), features));
        }
        return candidates;
    }

    /** The runner-up's for the best candidate, the best's for every other; 0 without a rival. */
    private static double rivalProbability(List<Scored> ranked, int position) {
        var rival = position == 0 ? 1 : 0;
        return rival < ranked.size() ? MatcherWeights.logistic(ranked.get(rival).logOdds()) : 0;
    }

    private static Set<String> withoutUnprepared(Set<String> states) {
        var result = new HashSet<>(states);
        result.remove(UNPREPARED);
        return result;
    }

    private static int difference(Set<String> from, Set<String> without) {
        return (int) from.stream().filter(state -> !without.contains(state)).count();
    }

    private record Scored(MatchableFood food, String matchedName, MatchFeatures features, double logOdds) {
    }
}
