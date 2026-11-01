package com.sterul.opencookbookapiserver.services.catalogue.relinking;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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

import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.entities.ReviewedRun;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.catalogue.IngredientRelinkLine;
import com.sterul.opencookbookapiserver.entities.catalogue.IngredientRelinkMember;
import com.sterul.opencookbookapiserver.entities.catalogue.IngredientRelinkProposal;
import com.sterul.opencookbookapiserver.entities.catalogue.IngredientRelinkRun;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientNeedRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRelinkProposalRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRelinkRunRepository;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.ReviewedRuns;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.catalogue.IngredientNames;
import com.sterul.opencookbookapiserver.services.catalogue.dataset.CatalogueDatasetReader;
import com.sterul.opencookbookapiserver.services.catalogue.linking.LinkSuggester;
import com.sterul.opencookbookapiserver.services.catalogue.linking.NameRuleService;
import com.sterul.opencookbookapiserver.services.catalogue.linking.RuleBook;
import com.sterul.opencookbookapiserver.services.catalogue.matching.CatalogueMatcher;
import com.sterul.opencookbookapiserver.services.catalogue.matching.ConfidenceBand;
import com.sterul.opencookbookapiserver.services.catalogue.matching.MatchCandidate;
import com.sterul.opencookbookapiserver.services.ingredients.IngredientLineReader;
import com.sterul.opencookbookapiserver.services.ingredients.IngredientLineReader.IngredientLine;

import lombok.extern.slf4j.Slf4j;

/**
 * Reviewed relinking: preview proposals per name group, decide, apply, revert. Person-made links are never touched, nor
 * the lines of their ingredients.
 */
@Service
@Transactional(rollbackFor = ApiException.class)
@Slf4j
public class RelinkService {

    private static final int SAMPLE_RECIPES = 5;

    private final IngredientRepository ingredientRepository;
    private final IngredientNeedRepository needRepository;
    private final IngredientService ingredientService;
    private final IngredientLineReader lineReader;
    private final IngredientRelinkRunRepository runRepository;
    private final IngredientRelinkProposalRepository proposalRepository;
    private final CatalogueFoodRepository foodRepository;
    private final RecipeRepository recipeRepository;
    private final LinkSuggester suggester;
    private final NameRuleService nameRules;
    private final CatalogueDatasetReader datasetReader;
    private final Clock clock;

    public RelinkService(IngredientRepository ingredientRepository, IngredientNeedRepository needRepository,
            IngredientService ingredientService, IngredientLineReader lineReader, IngredientRelinkRunRepository runRepository,
            IngredientRelinkProposalRepository proposalRepository, CatalogueFoodRepository foodRepository,
            RecipeRepository recipeRepository, LinkSuggester suggester, NameRuleService nameRules,
            CatalogueDatasetReader datasetReader, Clock clock) {
        this.ingredientRepository = ingredientRepository;
        this.needRepository = needRepository;
        this.ingredientService = ingredientService;
        this.lineReader = lineReader;
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

    /** Lines get one proposal per name and note, their owners' language and the food their name links to. */
    private record LineGroup(String name, String note, String language, CatalogueFood currentFood) {

        static LineGroup of(IngredientNeed need) {
            var ingredient = need.getIngredient();
            return new LineGroup(ingredient.getName(), need.getNote(), ingredient.getOwner().getLanguage(),
                    ingredient.getCatalogueFood());
        }
    }

    /** Changes no ingredient and no recipe line. */
    public IngredientRelinkRun preview(Scope scope, CookpalUser startedBy) {
        suggester.requireReady();
        var run = IngredientRelinkRun.builder()
                .scope(scope.kind()).belowConfidence(scope.belowConfidence())
                .matcherVersion(CatalogueMatcher.VERSION).datasetLabel(datasetReader.manifest().label())
                .build();
        run.setStartedBy(startedBy);
        var rules = nameRules.ruleBook();
        var proposals = new ArrayList<IngredientRelinkProposal>();
        var unchanged = scope.kind() == IngredientRelinkRun.Scope.NAMES_AND_NOTES
                ? readLinesAgain(run, rules, proposals)
                : relinkIngredients(run, scope, rules, proposals);
        run.setProposalCount(proposals.size());
        run.setIngredientCount(proposals.stream().mapToInt(proposal -> proposal.getMembers().size() + proposal.getLines().size())
                .sum());
        run.setUnchangedCount(unchanged);
        var saved = runRepository.save(run);
        proposalRepository.saveAll(proposals);
        log.info("Relink run {} previewed ({}): {} proposals for {} ingredients or lines, {} unchanged", saved.getId(), scope.kind(),
                proposals.size(), run.getIngredientCount(), unchanged);
        return saved;
    }

    /** @return how many ingredients keep their link */
    private int relinkIngredients(IngredientRelinkRun run, Scope scope, RuleBook rules, List<IngredientRelinkProposal> proposals) {
        var groups = inScope(scope).stream().collect(Collectors.groupingBy(Group::of, LinkedHashMap::new, Collectors.toList()));
        var suggestions = new LinkedHashMap<Group, MatchCandidate>();
        groups.keySet().forEach(group -> suggester.suggest(group.name(), group.language(), rules)
                .ifPresent(candidate -> suggestions.put(group, candidate)));
        var foods = foodsOf(suggestions.values());
        var unchanged = 0;
        for (var entry : groups.entrySet()) {
            var proposal = proposal(run, entry.getKey(), entry.getValue(), suggestions.get(entry.getKey()), foods);
            if (proposal.isPresent()) {
                proposals.add(proposal.get());
            } else {
                unchanged += entry.getValue().size();
            }
        }
        return unchanged;
    }

    /** Proposes what reads differently with today's catalogue. @return how many lines read as before */
    private int readLinesAgain(IngredientRelinkRun run, RuleBook rules, List<IngredientRelinkProposal> proposals) {
        var groups = needRepository.findAllOfIngredientsNoPersonDecided().stream()
                .collect(Collectors.groupingBy(LineGroup::of, LinkedHashMap::new, Collectors.toList()));
        var reads = new LinkedHashMap<LineGroup, IngredientLine>();
        groups.keySet().stream()
                .filter(group -> !rules.keepsAsWritten(IngredientLineReader.written(group.name(), group.note())))
                .forEach(group -> lineReader.read(null, "", group.name(), group.note(), group.language())
                        .filter(read -> !read.name().equals(group.name()) || !read.note().equals(group.note()))
                        .ifPresent(read -> reads.put(group, read)));
        var suggestions = new HashMap<LineGroup, MatchCandidate>();
        reads.forEach((group, read) -> suggester.suggest(read.name(), group.language(), rules)
                .ifPresent(candidate -> suggestions.put(group, candidate)));
        var foods = foodsOf(suggestions.values());
        reads.forEach((group, read) -> proposals.add(lineProposal(run, group, read, suggestions.get(group), foods,
                groups.get(group))));
        return groups.entrySet().stream().filter(entry -> !reads.containsKey(entry.getKey()))
                .mapToInt(entry -> entry.getValue().size()).sum();
    }

    /** @param remember for rejections: never link the name to the proposed food again, or never read the line otherwise */
    public List<IngredientRelinkProposal> decide(Long runId, Collection<Long> proposalIds, IngredientRelinkProposal.Decision decision,
            boolean remember, CookpalUser admin) {
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
    public IngredientRelinkRun apply(Long runId) {
        var run = previewed(runId);
        var accepted = ReviewedRuns.requireAnyAccepted(run,
                proposalRepository.findAllByRunAndDecision(run, IngredientRelinkProposal.Decision.ACCEPTED));
        var now = clock.instant();
        var applied = 0;
        var skipped = 0;
        for (var proposal : accepted) {
            var moved = moveLines(proposal);
            applied += moved;
            skipped += proposal.getLines().size() - moved;
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
    public IngredientRelinkRun revert(Long runId) {
        var run = ReviewedRuns.require(run(runId), ReviewedRun.Status.APPLIED);
        var reverted = 0;
        for (var proposal : proposalRepository.findAllByRunAndDecision(run, IngredientRelinkProposal.Decision.ACCEPTED)) {
            reverted += moveLinesBack(proposal);
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
        log.info("Relink run {} reverted for {} ingredients and lines", runId, reverted);
        return run;
    }

    public IngredientRelinkRun discard(Long runId) {
        var run = previewed(runId);
        run.discard();
        return run;
    }

    @Transactional(readOnly = true)
    public List<IngredientRelinkRun> getRuns() {
        return runRepository.findAllByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public IngredientRelinkRun getRun(Long runId) {
        return run(runId);
    }

    @Transactional(readOnly = true)
    public List<IngredientRelinkProposal> getProposals(Long runId) {
        return proposalRepository.findAllByRunOrderByNameAsc(run(runId));
    }

    @Transactional(readOnly = true)
    public List<Recipe> sampleRecipes(Long runId, Long proposalId) {
        var proposal = proposalRepository.findAllByRunAndIdIn(run(runId), List.of(proposalId)).stream().findFirst()
                .orElseThrow(ElementNotFound::new);
        var page = PageRequest.of(0, SAMPLE_RECIPES);
        return proposal.getChange() == IngredientRelinkProposal.Change.NAME_AND_NOTE
                ? recipeRepository.findUsingNeeds(proposal.getLines().stream().map(IngredientRelinkLine::getNeedId).toList(), page)
                : recipeRepository.findUsingIngredients(
                        proposal.getMembers().stream().map(IngredientRelinkMember::getIngredientId).toList(), page);
    }

    private List<Ingredient> inScope(Scope scope) {
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
            case NAMES_AND_NOTES -> throw new IllegalArgumentException("Names and notes are read from lines, not ingredients");
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
    private void rememberRejection(IngredientRelinkProposal proposal, CookpalUser admin) {
        if (proposal.getChange() == IngredientRelinkProposal.Change.NAME_AND_NOTE) {
            var written = IngredientLineReader.written(proposal.getName(), proposal.getNote());
            if (!nameRules.ruleBookFor(written).keepsAsWritten(written)) {
                nameRules.keepAsWritten(written, admin);
            }
            return;
        }
        var food = proposal.getNewFood();
        if (food != null && !nameRules.ruleBookFor(proposal.getName()).forbids(proposal.getName(), food.getCatalogueKey())) {
            nameRules.neverLinkTo(proposal.getName(), food, admin);
        }
    }

    private IngredientRelinkRun run(Long runId) {
        return runRepository.findById(runId).orElseThrow(ElementNotFound::new);
    }

    private IngredientRelinkRun previewed(Long runId) {
        return ReviewedRuns.require(run(runId), ReviewedRun.Status.PREVIEWED);
    }

    private Map<String, CatalogueFood> foodsOf(Collection<MatchCandidate> suggestions) {
        return foodRepository.findAllByCatalogueKeyMapped(suggestions.stream().map(MatchCandidate::foodKey).toList());
    }

    /** Lines still named and noted as previewed. @return how many moved */
    private int moveLines(IngredientRelinkProposal proposal) {
        var needs = needsOf(proposal.getLines());
        var moved = 0;
        for (var line : proposal.getLines()) {
            var need = needs.get(line.getNeedId());
            if (need != null && isWritten(need, proposal.getName(), proposal.getNote())) {
                write(need, proposal.getNewName(), proposal.getNewNote());
                line.setApplied(true);
                moved++;
            }
        }
        return moved;
    }

    /** Lines still as applied. @return how many moved back */
    private int moveLinesBack(IngredientRelinkProposal proposal) {
        var needs = needsOf(proposal.getLines());
        var moved = 0;
        for (var line : proposal.getLines()) {
            var need = needs.get(line.getNeedId());
            if (line.isApplied() && need != null && isWritten(need, proposal.getNewName(), proposal.getNewNote())) {
                write(need, proposal.getName(), proposal.getNote());
                moved++;
            }
        }
        return moved;
    }

    /** To the owner's ingredient of that name, created and linked as any new one; the one left behind may be unused. */
    private void write(IngredientNeed need, String name, String note) {
        need.setIngredient(ingredientService.createOrGetIngredient(Ingredient.builder().name(name).build(),
                need.getIngredient().getOwner()));
        need.setNote(note);
    }

    private Map<Long, IngredientNeed> needsOf(Collection<IngredientRelinkLine> lines) {
        return needRepository.findAllById(lines.stream().map(IngredientRelinkLine::getNeedId).toList()).stream()
                .collect(Collectors.toMap(IngredientNeed::getId, Function.identity()));
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

    private static boolean isWritten(IngredientNeed need, String name, String note) {
        return need.getIngredient().getName().equals(name) && need.getNote().equals(note);
    }

    private static IngredientRelinkProposal lineProposal(IngredientRelinkRun run, LineGroup group, IngredientLine read,
            MatchCandidate suggestion, Map<String, CatalogueFood> foods, List<IngredientNeed> needs) {
        var newFood = suggestion == null ? null : foods.get(suggestion.foodKey());
        return IngredientRelinkProposal.builder()
                .run(run).name(group.name()).note(group.note()).language(group.language())
                .newName(read.name()).newNote(read.note())
                .oldFood(group.currentFood()).newFood(newFood).newConfidence(newFood == null ? null : (float) suggestion.confidence())
                .change(IngredientRelinkProposal.Change.NAME_AND_NOTE).decision(IngredientRelinkProposal.Decision.PENDING)
                .lines(needs.stream()
                        .map(need -> IngredientRelinkLine.builder().needId(need.getId()).build())
                        .collect(Collectors.toCollection(ArrayList::new)))
                .build();
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
