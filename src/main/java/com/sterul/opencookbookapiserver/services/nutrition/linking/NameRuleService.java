package com.sterul.opencookbookapiserver.services.nutrition.linking;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueNameRule;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.CatalogueNameRuleRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.nutrition.IngredientNames;

import lombok.extern.slf4j.Slf4j;

@Service
@ConditionalOnNutritionEnabled
@Transactional(rollbackFor = ApiException.class)
@Slf4j
public class NameRuleService {

    private final CatalogueNameRuleRepository repository;

    public NameRuleService(CatalogueNameRuleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public RuleBook ruleBook() {
        return new RuleBook(repository.findAll());
    }

    @Transactional(readOnly = true)
    public RuleBook ruleBookFor(String name) {
        return new RuleBook(repository.findAllByName(IngredientNames.normalise(name)));
    }

    @Transactional(readOnly = true)
    public List<CatalogueNameRule> getRules() {
        return repository.findAllByOrderByNameAsc();
    }

    public CatalogueNameRule neverLinkTo(String name, CatalogueFood food, CookpalUser admin) throws ApiException {
        return save(CatalogueNameRule.builder().name(IngredientNames.normalise(name)).kind(CatalogueNameRule.Kind.NEVER_LINK_TO)
                .catalogueFood(food).createdBy(admin).build());
    }

    public CatalogueNameRule notAFood(String name, CookpalUser admin) throws ApiException {
        return save(CatalogueNameRule.builder().name(IngredientNames.normalise(name)).kind(CatalogueNameRule.Kind.NOT_A_FOOD)
                .createdBy(admin).build());
    }

    public void deleteRule(Long id) throws ElementNotFound {
        var rule = repository.findById(id).orElseThrow(ElementNotFound::new);
        log.info("Admin: Deleting name rule {} '{}'", rule.getKind(), rule.getName());
        repository.delete(rule);
    }

    private CatalogueNameRule save(CatalogueNameRule rule) throws ApiException {
        log.info("Admin: Adding name rule {} '{}'", rule.getKind(), rule.getName());
        try {
            return repository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException alreadyThere) {
            throw new ApiException(ApiErrorCode.CONFLICT, "That rule exists already");
        }
    }
}
