package com.sterul.opencookbookapiserver.services.nutrition.relinking;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.ReviewedRun;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.nutrition.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.nutrition.IngredientRelinkMember;
import com.sterul.opencookbookapiserver.entities.nutrition.IngredientRelinkProposal;
import com.sterul.opencookbookapiserver.entities.nutrition.IngredientRelinkRun;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRelinkProposalRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRelinkRunRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.ReviewedRuns;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.nutrition.IngredientNames;
import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDatasetReader;
import com.sterul.opencookbookapiserver.services.nutrition.linking.LinkSuggester;
import com.sterul.opencookbookapiserver.services.nutrition.linking.NameRuleService;
import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueMatcher;
import com.sterul.opencookbookapiserver.services.nutrition.matching.ConfidenceBand;
import com.sterul.opencookbookapiserver.services.nutrition.matching.MatchCandidate;

import lombok.extern.slf4j.Slf4j;

/** Reviewed relinking: preview proposals per name group, decide, apply, revert. Person-made links are never touched. */
@Service
@ConditionalOnNutritionEnabled
@Transactional(rollbackFor = ApiException.class)
@Slf4j
public class RelinkService {

    private static final int SAMPLE_RECIPES = 5;

    private final IngredientRepository ingredientRepository;
    private final IngredientRelinkRunRepository runRepository;
    private final IngredientRelinkProposalRepository proposalRepository;
    private final CatalogueFoodRepository foodRepository;
    private final RecipeRepository recipeRepository;
    private final LinkSuggester suggester;
    private final NameRuleService nameRules;
    private final NutritionDatasetReader datasetReader;
    private final Clock clock;

    public RelinkService(IngredientRepository ingredientRepository, IngredientRelinkRunRepository runRepository,
            IngredientRelinkProposalRepository proposalRepository, CatalogueFoodRepository foodRepository,
            RecipeRepository recipeRepository, LinkSuggester suggester, NameRuleService nameRules,
            NutritionDatasetReader datasetReader, Clock clock) {
        this.ingredientRepository = ingredientRepository;
        this.runRepository = runRepository;
        this.proposalRepository = proposalRepository;
        this.foodRepository = foodRepository;
        this.recipeRepository = recipeRepository;
        this.suggester = suggester;
        this.nameRules = nameRules;
        this.datasetReader = datasetReader;
        this.clock = clock;
    }

    public record Scope(IngredientRelinkRun.Scope kind, Float belowConfidence) {
    }

    /** Ingredients get one proposal per name, language, current food and confidence band. */
    private record Group(String name, String language, CatalogueFood currentFood, ConfidenceBand currentBand) {

        static Group of(Ingredient ingredient) {
            return new Group(IngredientNames.normalise(ingredient.getName()), ingredient.getOwner().getLanguage(),
                    ingredient.getCatalogueFood(), bandOf(ingredient.getLinkConfidence()));
        }
    }

    /** Changes no ingredient. */
    public IngredientRelinkRun preview(Scope scope, CookpalUser startedBy) throws ApiException {
        suggester.requireReady();
        var groups = inScope(scope).stream().collect(Collectors.groupingBy(Group::of, LinkedHashMap::new, Collectors.toList()));
        var rules = nameRules.ruleBook();
        var suggestions = new LinkedHashMap<Group, MatchCandidate>();
        groups.keySet().forEach(group -> suggester.suggest(group.name(), group.language(), rules)
                .ifPresent(candidate -> suggestions.put(group, candidate)));
        var foods = foodRepository.findAllByCatalogueKeyMapped(suggestions.values().stream().map(MatchCandidate::foodKey).toList());

        var run = IngredientRelinkRun.builder()
                .scope(scope.kind()).belowConfidence(scope.belowConfidence())
                .matcherVersion(CatalogueMatcher.VERSION).datasetLabel(datasetReader.manifest().label())
                .build();
        run.setStartedBy(startedBy);
        var proposals = new ArrayList<IngredientRelinkProposal>();
        var unchanged = 0;
        for (var entry : groups.entrySet()) {
            var proposal = proposal(run, entry.getKey(), entry.getValue(), suggestions.get(entry.getKey()), foods);
            if (proposal.isPresent()) {
                proposals.add(proposal.get());
            } else {
                unchanged += entry.getValue().size();
            }
        }
        run.setProposalCount(proposals.size());
        run.setIngredientCount(proposals.stream().mapToInt(proposal -> proposal.getMembers().size()).sum());
        run.setUnchangedCount(unchanged);
        var saved = runRepository.save(run);
        proposalRepository.saveAll(proposals);
        log.info("Relink run {} previewed ({}): {} proposals for {} ingredients, {} unchanged", saved.getId(), scope.kind(),
                proposals.size(), run.getIngredientCount(), unchanged);
        return saved;
    }

    /** @param remember for rejections: never link the name to the proposed food again */
    public List<IngredientRelinkProposal> decide(Long runId, Collection<Long> proposalIds, IngredientRelinkProposal.Decision decision,
            boolean remember, CookpalUser admin) throws ApiException {
        var run = previewed(runId);
        var proposals = proposalRepository.findAllByRunAndIdIn(run, proposalIds);
        for (var proposal : proposals) {
            proposal.setDecision(decision);
            if (decision == IngredientRelinkProposal.Decision.REJECTED && remember) {
                rememberRejection(proposal, admin);
            }
        }
        return proposals;
    }

    /** Skips ingredients changed since the preview. */
    public IngredientRelinkRun apply(Long runId) throws ApiException {
        var run = previewed(runId);
        var accepted = ReviewedRuns.requireAnyAccepted(run,
                proposalRepository.findAllByRunAndDecision(run, IngredientRelinkProposal.Decision.ACCEPTED));
        var now = clock.instant();
        var applied = 0;
        var skipped = 0;
        for (var proposal : accepted) {
            var ingredients = ingredientsOf(proposal.getMembers());
            for (var member : proposal.getMembers()) {
                var ingredient = ingredients.get(member.getIngredientId());
                if (ingredient == null || !Objects.equals(ingredient.getLastChange(), member.getPreviewedLastChange())) {
                    skipped++;
                    continue;
                }
                ingredient.linkAutomatically(proposal.getNewFood(), proposal.getNewConfidence(), run.getMatcherVersion(), now, run);
                member.setApplied(true);
                applied++;
            }
        }
        run.markApplied(now, applied, skipped);
        log.info("Relink run {} applied to {} ingredients, {} skipped as changed since the preview", run.getId(), applied, skipped);
        return run;
    }

    /** Restores only ingredients this run was the last to link. */
    public IngredientRelinkRun revert(Long runId) throws ApiException {
        var run = ReviewedRuns.require(run(runId), ReviewedRun.Status.APPLIED);
        var reverted = 0;
        for (var proposal : proposalRepository.findAllByRunAndDecision(run, IngredientRelinkProposal.Decision.ACCEPTED)) {
            var ingredients = ingredientsOf(proposal.getMembers());
            var oldFoods = foodRepository.findAllById(proposal.getMembers().stream().map(IngredientRelinkMember::getOldFoodId)
                    .filter(Objects::nonNull).toList()).stream().collect(Collectors.toMap(CatalogueFood::getId, Function.identity()));
            for (var member : proposal.getMembers()) {
                var ingredient = ingredients.get(member.getIngredientId());
                if (member.isApplied() && ingredient != null && ingredient.getLinkRun() != null
                        && run.getId().equals(ingredient.getLinkRun().getId())) {
                    ingredient.restoreLink(member.getOldFoodId() == null ? null : oldFoods.get(member.getOldFoodId()),
                            member.getOldLinkSource(), member.getOldLinkConfidence(), member.getOldLinkMatcherVersion(),
                            member.getOldLinkedAt());
                    reverted++;
                }
            }
        }
        run.markReverted(clock.instant());
        log.info("Relink run {} reverted for {} ingredients", runId, reverted);
        return run;
    }

    public IngredientRelinkRun discard(Long runId) throws ApiException {
        var run = previewed(runId);
        run.discard();
        return run;
    }

    @Transactional(readOnly = true)
    public List<IngredientRelinkRun> getRuns() {
        return runRepository.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public IngredientRelinkRun getRun(Long runId) throws ElementNotFound {
        return run(runId);
    }

    @Transactional(readOnly = true)
    public List<IngredientRelinkProposal> getProposals(Long runId) throws ElementNotFound {
        return proposalRepository.findAllByRunOrderByNameAsc(run(runId));
    }

    @Transactional(readOnly = true)
    public List<Recipe> sampleRecipes(Long runId, Long proposalId) throws ElementNotFound {
        var proposal = proposalRepository.findAllByRunAndIdIn(run(runId), List.of(proposalId)).stream().findFirst()
                .orElseThrow(ElementNotFound::new);
        return recipeRepository.findUsingIngredients(
                proposal.getMembers().stream().map(IngredientRelinkMember::getIngredientId).toList(), PageRequest.of(0, SAMPLE_RECIPES));
    }

    private List<Ingredient> inScope(Scope scope) throws ApiException {
        return switch (scope.kind()) {
            case NEVER_MATCHED_OR_UNLINKED -> ingredientRepository.findNeverMatchedOrUnlinked();
            case AUTOMATIC_BELOW_CONFIDENCE -> {
                if (scope.belowConfidence() == null) {
                    throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "Say below which confidence links are in scope");
                }
                yield ingredientRepository.findLinkedAutomaticallyBelow(scope.belowConfidence());
            }
            case ALL_AUTOMATIC -> ingredientRepository.findLinkedAutomatically();
            case RETIRED_FOODS -> ingredientRepository.findLinkedAutomaticallyToRetiredFoods();
            case OLDER_MATCHER -> ingredientRepository.findLinkedAutomaticallyBefore(CatalogueMatcher.VERSION);
        };
    }

    private Optional<IngredientRelinkProposal> proposal(IngredientRelinkRun run, Group group, List<Ingredient> members,
            MatchCandidate suggestion, Map<String, CatalogueFood> foods) {
        var newFood = suggestion == null ? null : foods.get(suggestion.foodKey());
        var newConfidence = newFood == null ? null : (float) suggestion.confidence();
        var change = change(group, newFood, bandOf(newConfidence));
        if (change == null) {
            return Optional.empty();
        }
        return Optional.of(IngredientRelinkProposal.builder()
                .run(run).name(group.name()).language(group.language())
                .oldFood(group.currentFood()).newFood(newFood).newConfidence(newConfidence)
                .change(change).decision(IngredientRelinkProposal.Decision.PENDING)
                .members(members.stream().map(RelinkService::member).collect(Collectors.toCollection(ArrayList::new)))
                .build());
    }

    /** Unlinking needs no rule: rejecting it keeps the link. */
    private void rememberRejection(IngredientRelinkProposal proposal, CookpalUser admin) throws ApiException {
        var food = proposal.getNewFood();
        if (food != null && !nameRules.ruleBookFor(proposal.getName()).forbids(proposal.getName(), food.getCatalogueKey())) {
            nameRules.neverLinkTo(proposal.getName(), food, admin);
        }
    }

    private IngredientRelinkRun run(Long runId) throws ElementNotFound {
        return runRepository.findById(runId).orElseThrow(ElementNotFound::new);
    }

    private IngredientRelinkRun previewed(Long runId) throws ApiException {
        return ReviewedRuns.require(run(runId), ReviewedRun.Status.PREVIEWED);
    }

    private Map<Long, Ingredient> ingredientsOf(Collection<IngredientRelinkMember> members) {
        return ingredientRepository.findAllById(members.stream().map(IngredientRelinkMember::getIngredientId).toList()).stream()
                .collect(Collectors.toMap(Ingredient::getId, Function.identity()));
    }

    /** Null when nothing changes. */
    private static IngredientRelinkProposal.Change change(Group group, CatalogueFood proposed, ConfidenceBand proposedBand) {
        var current = group.currentFood();
        if (Objects.equals(current, proposed)) {
            return current == null || group.currentBand() == proposedBand ? null : IngredientRelinkProposal.Change.CONFIDENCE;
        }
        if (current == null) {
            return IngredientRelinkProposal.Change.NEW_LINK;
        }
        return proposed == null ? IngredientRelinkProposal.Change.UNLINKED : IngredientRelinkProposal.Change.CHANGED;
    }

    /** Migrated links have no confidence. */
    private static ConfidenceBand bandOf(Float confidence) {
        return confidence == null ? ConfidenceBand.NONE : ConfidenceBand.of(confidence);
    }

    private static IngredientRelinkMember member(Ingredient ingredient) {
        var food = ingredient.getCatalogueFood();
        return IngredientRelinkMember.builder()
                .ingredientId(ingredient.getId())
                .previewedLastChange(ingredient.getLastChange())
                .oldFoodId(food == null ? null : food.getId())
                .oldLinkSource(ingredient.getLinkSource())
                .oldLinkConfidence(ingredient.getLinkConfidence())
                .oldLinkMatcherVersion(ingredient.getLinkMatcherVersion())
                .oldLinkedAt(ingredient.getLinkedAt())
                .build();
    }
}
