package com.sterul.opencookbookapiserver.controllers.seasonal;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.sterul.opencookbookapiserver.entities.seasonal.SeasonCategory;
import com.sterul.opencookbookapiserver.entities.seasonal.SeasonMonth;
import com.sterul.opencookbookapiserver.entities.seasonal.SeasonalProduce;
import com.sterul.opencookbookapiserver.entities.seasonal.StorageFacts;

/**
 * @param id       the catalogue food
 * @param icon     an icon name the app bundles, Fluent Emoji or CookPal's own; null shows the produce aisle's icon
 * @param months   month 1 to 12 -> how the food comes to market then, least climate impact first; months without
 *                 any are left out
 * @param inSeason the months it counts as in season
 * @param storage  null when the sources agree on nothing
 */
public record SeasonalFoodResponse(Long id, String displayName, String icon, SeasonCategory category,
        Map<Integer, List<SeasonMonth.GrowingMethod>> months, List<Integer> inSeason, Storage storage) {

    /** @param place null when the sources disagree */
    public record Storage(StorageFacts.Place place, boolean coldSensitive, boolean ethyleneProducer,
            boolean ethyleneSensitive) {

        static Storage of(StorageFacts facts) {
            if (facts == null || facts.getPlace() == null && !facts.isColdSensitive() && !facts.isEthyleneProducer()
                    && !facts.isEthyleneSensitive()) {
                return null;
            }
            return new Storage(facts.getPlace(), facts.isColdSensitive(), facts.isEthyleneProducer(),
                    facts.isEthyleneSensitive());
        }
    }

    public static SeasonalFoodResponse of(SeasonalProduce produce, String region, String language) {
        var food = produce.getFood();
        var months = produce.getMonths().stream()
                .filter(season -> season.getRegion().equals(region))
                .collect(Collectors.groupingBy(season -> (int) season.getMonth(), TreeMap::new,
                        Collectors.mapping(SeasonMonth::getMethod, Collectors.collectingAndThen(Collectors.toList(),
                                methods -> methods.stream().sorted().toList()))));
        var inSeason = IntStream.rangeClosed(1, 12).filter(month -> produce.isInSeason(region, month)).boxed().toList();
        return new SeasonalFoodResponse(food.getId(), food.displayName(language).orElse(food.getSourceName()),
                food.shoppingIcon(), produce.getCategory(), months, inSeason, Storage.of(produce.getStorage()));
    }
}
