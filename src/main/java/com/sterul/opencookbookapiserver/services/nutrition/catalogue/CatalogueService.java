package com.sterul.opencookbookapiserver.services.nutrition.catalogue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodName;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFoodPortion;
import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;
import com.sterul.opencookbookapiserver.entities.nutrition.NutritionDatasetImport;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.NutritionDatasetImportRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.nutrition.UnitLexicon;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;

import lombok.extern.slf4j.Slf4j;

/** Catalogue administration: dataset foods only get names; custom foods can be edited, merged and deleted. */
@Service
@ConditionalOnNutritionEnabled
@Transactional(rollbackFor = ApiException.class)
@Slf4j
public class CatalogueService {

    private final CatalogueFoodRepository foodRepository;
    private final IngredientRepository ingredientRepository;
    private final CatalogueFoodReferences references;
    private final NutritionDatasetImportRepository importRepository;
    private final UnitLexicon unitLexicon;
    private final ApplicationEventPublisher events;

    public CatalogueService(CatalogueFoodRepository foodRepository, IngredientRepository ingredientRepository,
            CatalogueFoodReferences references, NutritionDatasetImportRepository importRepository, UnitLexicon unitLexicon,
            ApplicationEventPublisher events) {
        this.foodRepository = foodRepository;
        this.ingredientRepository = ingredientRepository;
        this.references = references;
        this.importRepository = importRepository;
        this.unitLexicon = unitLexicon;
        this.events = events;
    }

    public record CustomFood(List<CatalogueFoodName> names, NutrientValues nutrients, Float densityGPerMl,
            boolean negligible, List<CatalogueFoodPortion> portions) {
    }

    public List<CatalogueFood> getAllFoods() {
        return foodRepository.findAll();
    }

    public CatalogueFood getFood(Long id) throws ElementNotFound {
        return foodRepository.findById(id).orElseThrow(ElementNotFound::new);
    }

    public long countLinkedIngredients(CatalogueFood food) {
        return ingredientRepository.countByCatalogueFood(food);
    }

    public List<NutritionDatasetImport> getImports() {
        return importRepository.findAllByOrderByStartedAtDesc();
    }

    public CatalogueFood createCustomFood(CustomFood custom) throws ApiException {
        var food = CatalogueFood.builder()
                .catalogueKey("custom-" + UUID.randomUUID())
                .origin(CatalogueFood.Origin.CUSTOM)
                .build();
        applyCustom(food, custom);
        log.info("Admin: Creating custom catalogue food {}", food.getCatalogueKey());
        var created = foodRepository.save(food);
        changed("created " + created.getCatalogueKey());
        return created;
    }

    public CatalogueFood updateCustomFood(Long id, CustomFood custom) throws ApiException {
        var food = requireCustom(getFood(id));
        applyCustom(food, custom);
        log.info("Admin: Updating custom catalogue food {}", food.getCatalogueKey());
        changed("updated " + food.getCatalogueKey());
        return food;
    }

    public void deleteCustomFood(Long id) throws ApiException {
        var food = requireCustom(getFood(id));
        if (countLinkedIngredients(food) > 0 || foodRepository.existsByVariantOf(food)) {
            throw new ApiException(ApiErrorCode.CONFLICT, "Catalogue food " + id + " is still referred to; merge it instead");
        }
        log.info("Admin: Deleting custom catalogue food {}", food.getCatalogueKey());
        foodRepository.delete(food);
        changed("deleted " + food.getCatalogueKey());
    }

    public CatalogueFood addName(Long id, String languageIsoCode, String name) throws ApiException {
        var food = getFood(id);
        var trimmed = name.trim();
        var added = CatalogueFoodName.adminAlias(languageIsoCode, trimmed);
        if (food.getNames().stream().anyMatch(added::sameAs)) {
            throw new ApiException(ApiErrorCode.CONFLICT, "Catalogue food " + food.getCatalogueKey() + " already has the name '" + trimmed + "'");
        }
        requireNameFree(languageIsoCode, trimmed, food);
        food.getNames().add(added);
        log.info("Admin: Adding name '{}' ({}) to catalogue food {}", trimmed, languageIsoCode, food.getCatalogueKey());
        changed("named " + food.getCatalogueKey());
        return food;
    }

    /** Only administrator names can be removed. */
    public CatalogueFood removeName(Long id, String languageIsoCode, String name) throws ApiException {
        var food = getFood(id);
        var unwanted = CatalogueFoodName.adminAlias(languageIsoCode, name);
        var removed = food.getNames()
                .removeIf(existing -> existing.getOrigin() == CatalogueFoodName.Origin.ADMIN && existing.sameAs(unwanted));
        if (!removed) {
            throw new ElementNotFound();
        }
        changed("unnamed " + food.getCatalogueKey());
        return food;
    }

    public CatalogueFood mergeCustomFood(Long sourceId, Long targetId) throws ApiException {
        var source = requireCustom(getFood(sourceId));
        var target = getFood(targetId);
        if (source.equals(target)) {
            throw new ApiException(ApiErrorCode.CONFLICT, "A catalogue food cannot be merged into itself");
        }
        var relinked = references.move(source, target);
        var movedNames = source.getNames().stream()
                .filter(name -> target.getNames().stream().noneMatch(name::sameAs))
                .map(name -> CatalogueFoodName.adminAlias(name.getLanguageIsoCode(), name.getName()))
                .toList();
        source.getNames().clear();
        foodRepository.saveAndFlush(source);
        target.getNames().addAll(movedNames);
        foodRepository.delete(source);
        log.info("Admin: Merged catalogue food {} into {}, {} ingredients relinked", source.getCatalogueKey(),
                target.getCatalogueKey(), relinked);
        changed("merged " + source.getCatalogueKey() + " into " + target.getCatalogueKey());
        return target;
    }

    private void changed(String reason) {
        events.publishEvent(new CatalogueChangedEvent(reason));
    }

    private void applyCustom(CatalogueFood food, CustomFood custom) throws ApiException {
        var names = new ArrayList<CatalogueFoodName>();
        for (var name : custom.names()) {
            if (names.stream().anyMatch(name::sameAs)) {
                throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "The name '" + name.getName() + "' is given twice");
            }
            requireNameFree(name.getLanguageIsoCode(), name.getName().trim(), food);
            names.add(CatalogueFoodName.builder().languageIsoCode(name.getLanguageIsoCode()).name(name.getName().trim())
                    .display(name.isDisplay()).origin(CatalogueFoodName.Origin.ADMIN).build());
        }
        for (var portion : custom.portions()) {
            var unit = unitLexicon.unit(portion.getUnitKey());
            if (unit.isEmpty() || unit.get().kind() != NutritionDataset.UnitKind.COUNT || unit.get().sizeOf() != null) {
                throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "'" + portion.getUnitKey() + "' is not a count unit");
            }
        }
        food.getNames().clear();
        food.getNames().addAll(names);
        food.setNutrients(custom.nutrients());
        food.setDensityGPerMl(custom.densityGPerMl());
        food.setNegligible(custom.negligible());
        food.getPortions().clear();
        food.getPortions().addAll(custom.portions().stream()
                .map(portion -> CatalogueFoodPortion.builder().unitKey(portion.getUnitKey()).grams(portion.getGrams())
                        .origin(CatalogueFoodPortion.Origin.ADMIN).build())
                .toList());
    }

    private void requireNameFree(String languageIsoCode, String name, CatalogueFood food) throws ApiException {
        var owner = foodRepository.findByName(languageIsoCode, name);
        if (owner.isPresent() && !owner.get().equals(food)) {
            throw new ApiException(ApiErrorCode.CONFLICT,
                    "The name '" + name + "' belongs to catalogue food " + owner.get().getCatalogueKey());
        }
    }

    private static CatalogueFood requireCustom(CatalogueFood food) throws ApiException {
        if (food.isReadOnly()) {
            throw new ApiException(ApiErrorCode.CONFLICT, "Catalogue food " + food.getCatalogueKey() + " ships with the dataset and is read-only");
        }
        return food;
    }
}
