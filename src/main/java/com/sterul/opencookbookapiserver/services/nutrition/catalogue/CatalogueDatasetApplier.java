package com.sterul.opencookbookapiserver.services.nutrition.catalogue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodName;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies a dataset release in one transaction: upserts dataset foods by key, retires removed ones, keeps admin
 * names unless the release claims them, and merges legacy foods into dataset foods sharing a name.
 */
@Component
@Slf4j
public class CatalogueDatasetApplier {

    static final String LEGACY_KEY_PREFIX = "legacy-";

    private final CatalogueFoodRepository foodRepository;
    private final CatalogueFoodReferences references;
    private final EntityManager entityManager;

    public CatalogueDatasetApplier(CatalogueFoodRepository foodRepository, CatalogueFoodReferences references,
            EntityManager entityManager) {
        this.foodRepository = foodRepository;
        this.references = references;
        this.entityManager = entityManager;
    }

    @Transactional
    public List<String> apply(NutritionDataset.Catalogue catalogue) {
        var report = new ArrayList<String>();
        var shipped = catalogue.foods().stream()
                .collect(Collectors.toMap(NutritionDataset.Food::key, Function.identity(), (first, second) -> first, LinkedHashMap::new));
        var nameOwners = nameOwners(shipped.values());

        var foods = upsertDatasetFoods(shipped, report);
        retireMissing(shipped, report);
        var merges = legacyMerges(nameOwners, foods);

        // Names are unique across foods: remove all changing names in one flush before adding any.
        var finalNames = finalNames(shipped, foods, merges, nameOwners, report);
        finalNames.keySet().forEach(food -> food.getNames().clear());
        entityManager.flush();
        finalNames.forEach((food, names) -> food.getNames().addAll(names));
        entityManager.flush();

        merges.forEach((legacy, target) -> merge(legacy, target, report));
        log.info("Nutrition dataset applied: {} foods", foods.size());
        return report;
    }

    private Map<String, CatalogueFood> upsertDatasetFoods(Map<String, NutritionDataset.Food> shipped, List<String> report) {
        var existing = foodRepository.findAllByOrigin(CatalogueFood.Origin.DATASET).stream()
                .collect(Collectors.toMap(CatalogueFood::getCatalogueKey, Function.identity()));
        var foods = new HashMap<String, CatalogueFood>();
        var created = 0;
        for (var food : shipped.values()) {
            var entity = existing.get(food.key());
            if (entity == null) {
                entity = CatalogueFood.builder().catalogueKey(food.key()).origin(CatalogueFood.Origin.DATASET).build();
                created++;
            }
            DatasetFoods.copyInto(food, entity);
            foods.put(food.key(), foodRepository.save(entity));
        }
        for (var food : shipped.values()) {
            foods.get(food.key()).setVariantOf(food.variantOf() == null ? null : foods.get(food.variantOf()));
        }
        report.add(created + " foods added, " + (shipped.size() - created) + " foods updated");
        return foods;
    }

    private void retireMissing(Map<String, NutritionDataset.Food> shipped, List<String> report) {
        var retired = foodRepository.findAllByOrigin(CatalogueFood.Origin.DATASET).stream()
                .filter(food -> !shipped.containsKey(food.getCatalogueKey()) && !food.isRetired())
                .toList();
        retired.forEach(food -> {
            food.setRetired(true);
            report.add("retired " + food.getCatalogueKey() + " (no longer in the dataset)");
        });
    }

    /** Each legacy food maps to the dataset food owning one of its names, display name first. */
    private Map<CatalogueFood, CatalogueFood> legacyMerges(Map<NameKey, String> nameOwners, Map<String, CatalogueFood> foods) {
        var merges = new LinkedHashMap<CatalogueFood, CatalogueFood>();
        foodRepository.findAllByOrigin(CatalogueFood.Origin.CUSTOM).stream()
                .filter(food -> food.getCatalogueKey().startsWith(LEGACY_KEY_PREFIX))
                .forEach(legacy -> legacy.getNames().stream()
                        .sorted((first, second) -> Boolean.compare(second.isDisplay(), first.isDisplay()))
                        .map(name -> nameOwners.get(NameKey.of(name)))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .ifPresent(targetKey -> merges.put(legacy, foods.get(targetKey))));
        return merges;
    }

    /** Final names of every food whose names change. */
    private Map<CatalogueFood, List<CatalogueFoodName>> finalNames(Map<String, NutritionDataset.Food> shipped,
            Map<String, CatalogueFood> foods, Map<CatalogueFood, CatalogueFood> merges, Map<NameKey, String> nameOwners,
            List<String> report) {
        var result = new LinkedHashMap<CatalogueFood, List<CatalogueFoodName>>();
        var movedToTarget = new HashMap<CatalogueFood, List<CatalogueFoodName>>();
        for (var merge : merges.entrySet()) {
            movedToTarget.computeIfAbsent(merge.getValue(), target -> new ArrayList<>())
                    .addAll(merge.getKey().getNames().stream()
                            .map(name -> CatalogueFoodName.adminAlias(name.getLanguageIsoCode(), name.getName()))
                            .toList());
            result.put(merge.getKey(), List.of());
        }

        for (var food : shipped.values()) {
            var entity = foods.get(food.key());
            var names = new ArrayList<CatalogueFoodName>();
            var taken = new HashSet<NameKey>();
            for (var name : food.names()) {
                var datasetName = CatalogueFoodName.builder().languageIsoCode(name.language()).name(name.name())
                        .display(name.display()).origin(CatalogueFoodName.Origin.DATASET).build();
                names.add(datasetName);
                taken.add(NameKey.of(datasetName));
            }
            var adminNames = new ArrayList<>(entity.getNames().stream()
                    .filter(name -> name.getOrigin() == CatalogueFoodName.Origin.ADMIN).toList());
            adminNames.addAll(movedToTarget.getOrDefault(entity, List.of()));
            for (var name : adminNames) {
                var key = NameKey.of(name);
                var owner = nameOwners.get(key);
                if (owner != null && !owner.equals(food.key())) {
                    report.add("administrator name '" + name.getName() + "' of " + food.key() + " dropped, the dataset gives it to " + owner);
                } else if (taken.add(key)) {
                    names.add(name);
                }
            }
            if (!sameNames(entity.getNames(), names)) {
                result.put(entity, names);
            }
        }

        foodRepository.findAllByOrigin(CatalogueFood.Origin.CUSTOM).stream()
                .filter(custom -> !merges.containsKey(custom))
                .forEach(custom -> {
                    var kept = custom.getNames().stream().filter(name -> !nameOwners.containsKey(NameKey.of(name))).toList();
                    if (kept.size() != custom.getNames().size()) {
                        report.add("names of custom food " + custom.getCatalogueKey() + " dropped, the dataset gives them to dataset foods");
                        result.put(custom, new ArrayList<>(kept));
                    }
                });
        return result;
    }

    private void merge(CatalogueFood legacy, CatalogueFood target, List<String> report) {
        var moved = references.move(legacy, target);
        foodRepository.delete(legacy);
        report.add("merged " + legacy.getCatalogueKey() + " into " + target.getCatalogueKey() + " (" + moved + " ingredients relinked)");
    }

    private static Map<NameKey, String> nameOwners(Iterable<NutritionDataset.Food> foods) {
        var owners = new HashMap<NameKey, String>();
        for (var food : foods) {
            food.names().forEach(name -> owners.put(NameKey.of(name.language(), name.name()), food.key()));
        }
        return owners;
    }

    private static boolean sameNames(List<CatalogueFoodName> current, List<CatalogueFoodName> wanted) {
        return current.size() == wanted.size() && new HashSet<>(current).equals(new HashSet<>(wanted));
    }

    private record NameKey(String language, String lowerCaseName) {
        static NameKey of(CatalogueFoodName name) {
            return of(name.getLanguageIsoCode(), name.getName());
        }

        static NameKey of(String language, String name) {
            return new NameKey(language, name.toLowerCase(Locale.ROOT));
        }
    }
}
