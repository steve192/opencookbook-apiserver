package com.sterul.opencookbookapiserver.services.nutrition.reports;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.projections.IngredientUseCount;
import com.sterul.opencookbookapiserver.services.nutrition.IngredientNames;
import com.sterul.opencookbookapiserver.services.nutrition.linking.NameRuleService;
import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueMatcher;
import com.sterul.opencookbookapiserver.services.nutrition.matching.ConfidenceBand;
import com.sterul.opencookbookapiserver.services.nutrition.matching.MatchCandidate;

/** The ingredient names users write, and how well the catalogue answers them. */
@Service
@ConditionalOnNutritionEnabled
@Transactional(readOnly = true)
public class IngredientNameReport {

    /** The gold set refuses names still labelled so. */
    private static final String UNLABELLED = "?";
    private static final String NOTHING_FITS = "-";
    private static final int CANDIDATES = 3;

    private final IngredientRepository ingredientRepository;
    private final CatalogueFoodRepository foodRepository;
    private final CatalogueMatcher matcher;
    private final NameRuleService nameRules;

    public IngredientNameReport(IngredientRepository ingredientRepository, CatalogueFoodRepository foodRepository,
            CatalogueMatcher matcher, NameRuleService nameRules) {
        this.ingredientRepository = ingredientRepository;
        this.foodRepository = foodRepository;
        this.matcher = matcher;
        this.nameRules = nameRules;
    }

    private record Name(String name, String language) {
    }

    public record Candidate(CatalogueFood food, double confidence) {
    }

    /**
     * @param useCount   recipe lines using the name
     * @param candidates most likely first, without foods the rules forbid
     */
    public record UnmatchedName(String name, String language, long userCount, long useCount, List<Long> ingredientIds,
            List<Candidate> candidates) {
    }

    /** Undecided names not linked silently, the names most users wrote first. */
    public List<UnmatchedName> unmatchedNames(int limit) throws ApiException {
        matcher.requireReady();
        var uses = IngredientUseCount.asMap(ingredientRepository.countUsesGroupedByIngredient());
        var rules = nameRules.ruleBook();
        var unmatched = byName(ingredientRepository.findAllWithOwnerAndFood().stream()
                .filter(IngredientNameReport::isUnmatched)
                .filter(ingredient -> !rules.isNotAFood(ingredient.getName()))
                .toList());
        var ranked = unmatched.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Name, List<Ingredient>>>comparingLong(entry -> userCount(entry.getValue())).reversed()
                        .thenComparing(entry -> entry.getKey().name()))
                .limit(limit)
                .toList();
        var candidates = ranked.stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> matcher.rank(entry.getKey().name(), entry.getKey().language(), CANDIDATES).stream()
                        .filter(candidate -> !rules.forbids(entry.getKey().name(), candidate.foodKey()))
                        .toList()));
        var foods = foodRepository.findAllByCatalogueKeyMapped(candidates.values().stream().flatMap(List::stream).map(MatchCandidate::foodKey).toList());
        return ranked.stream()
                .map(entry -> new UnmatchedName(entry.getKey().name(), entry.getKey().language(), userCount(entry.getValue()),
                        occurrences(entry.getValue(), uses),
                        entry.getValue().stream().map(Ingredient::getId).toList(),
                        candidates.get(entry.getKey()).stream()
                                .filter(candidate -> foods.containsKey(candidate.foodKey()))
                                .map(candidate -> new Candidate(foods.get(candidate.foodKey()), candidate.confidence()))
                                .toList()))
                .toList();
    }

    /** Every name used in a recipe as a TSV draft of the production gold set, labelled with people's decisions. */
    public String productionNames() {
        var uses = IngredientUseCount.asMap(ingredientRepository.countUsesGroupedByIngredient());
        var used = byName(ingredientRepository.findAllWithOwnerAndFood().stream()
                .filter(ingredient -> uses.containsKey(ingredient.getId()))
                .toList());
        var tsv = new StringBuilder("# name\tlanguage\tcatalogue keys, - for none, ? for unlabelled\toccurrences\n");
        used.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Name, List<Ingredient>>>comparingLong(entry -> occurrences(entry.getValue(), uses)).reversed()
                        .thenComparing(entry -> entry.getKey().name()))
                .forEach(entry -> tsv.append(entry.getKey().name()).append('\t')
                        .append(Objects.requireNonNullElse(entry.getKey().language(), "")).append('\t')
                        .append(label(entry.getValue())).append('\t')
                        .append(occurrences(entry.getValue(), uses)).append('\n'));
        return tsv.toString();
    }

    private static boolean isUnmatched(Ingredient ingredient) {
        if (ingredient.isDecidedByPerson()) {
            return false;
        }
        return ingredient.getCatalogueFood() == null || ingredient.getLinkConfidence() == null
                || ConfidenceBand.of(ingredient.getLinkConfidence()) != ConfidenceBand.SILENT;
    }

    private static String label(List<Ingredient> ingredients) {
        var decided = ingredients.stream().filter(Ingredient::isDecidedByPerson).toList();
        if (decided.isEmpty()) {
            return UNLABELLED;
        }
        var keys = decided.stream().map(Ingredient::getCatalogueFood).filter(Objects::nonNull)
                .map(CatalogueFood::getCatalogueKey).distinct().sorted().collect(Collectors.joining("|"));
        return keys.isEmpty() ? NOTHING_FITS : keys;
    }

    private static Map<Name, List<Ingredient>> byName(List<Ingredient> ingredients) {
        return ingredients.stream().collect(Collectors.groupingBy(
                ingredient -> new Name(IngredientNames.normalise(ingredient.getName()), ingredient.getOwner().getLanguage()),
                LinkedHashMap::new, Collectors.toList()));
    }

    private static long occurrences(List<Ingredient> ingredients, Map<Long, Long> uses) {
        return ingredients.stream().mapToLong(ingredient -> uses.getOrDefault(ingredient.getId(), 0L)).sum();
    }

    private static long userCount(List<Ingredient> ingredients) {
        return ingredients.stream().map(ingredient -> ingredient.getOwner().getUserId()).distinct().count();
    }
}
