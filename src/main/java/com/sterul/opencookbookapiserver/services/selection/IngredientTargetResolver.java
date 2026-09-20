package com.sterul.opencookbookapiserver.services.selection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;

/** Turns the ingredients a cook picked into what counts as them while matching. */
@Component
public class IngredientTargetResolver {

    private final IngredientRepository ingredientRepository;
    private final CatalogueFoodRepository catalogueFoodRepository;

    public IngredientTargetResolver(IngredientRepository ingredientRepository,
            CatalogueFoodRepository catalogueFoodRepository) {
        this.ingredientRepository = ingredientRepository;
        this.catalogueFoodRepository = catalogueFoodRepository;
    }

    /** Ingredients of somebody else are silently dropped, so ids cannot be probed. */
    public List<MatchTarget> resolve(List<Long> ingredientIds, CookpalUser owner) {
        if (ingredientIds.isEmpty()) {
            return List.of();
        }
        var ingredients = ingredientRepository.findAllByIdInAndOwner(ingredientIds, owner);
        var families = familiesOf(ingredients);
        return ingredients.stream().map(ingredient -> toTarget(ingredient, families)).toList();
    }

    /** The foods of each picked food's family, by the id of the family's base. */
    private Map<Long, Set<Long>> familiesOf(List<Ingredient> ingredients) {
        var bases = ingredients.stream()
                .map(Ingredient::getCatalogueFood)
                .filter(Objects::nonNull)
                .map(IngredientTargetResolver::baseOf)
                .collect(Collectors.toMap(CatalogueFood::getId, Function.identity(), (first, duplicate) -> first));
        if (bases.isEmpty()) {
            return Map.of();
        }

        var families = new HashMap<Long, Set<Long>>();
        bases.keySet().forEach(baseId -> families.put(baseId, new HashSet<>(Set.of(baseId))));
        catalogueFoodRepository.findAllByVariantOfIn(bases.values()).forEach(variant -> families
                .computeIfAbsent(variant.getVariantOf().getId(), id -> new HashSet<>())
                .add(variant.getId()));
        return families;
    }

    /** A variant stands for its base, so picking one preparation finds the others. */
    private static CatalogueFood baseOf(CatalogueFood food) {
        return food.getVariantOf() == null ? food : food.getVariantOf();
    }

    private MatchTarget toTarget(Ingredient ingredient, Map<Long, Set<Long>> families) {
        var food = ingredient.getCatalogueFood();
        var foodIds = food == null ? Set.<Long>of() : families.getOrDefault(baseOf(food).getId(), Set.of());
        return new MatchTarget(ingredient.getId(), ingredient.getName(), foodIds);
    }
}
