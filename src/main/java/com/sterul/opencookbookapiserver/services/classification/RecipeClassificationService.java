package com.sterul.opencookbookapiserver.services.classification;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.ReviewedRun;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.recipe.ClassificationKind;
import com.sterul.opencookbookapiserver.entities.recipe.DerivedClassification;
import com.sterul.opencookbookapiserver.entities.recipe.Recipe;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeClassificationProposal;
import com.sterul.opencookbookapiserver.entities.recipe.RecipeClassificationRun;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.RecipeClassificationProposalRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeClassificationRunRepository;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.ReviewedRuns;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Classifies recipes under review: preview, decide, apply, revert. A value a person set is never
 * touched, and an unreadable recipe is skipped rather than guessed at. Works on encoded values only;
 * reading a recipe is up to the {@link RecipeClassifier} of the run's kind.
 */
@Service
@Slf4j
@Transactional
public class RecipeClassificationService {

    private final RecipeRepository recipeRepository;
    private final RecipeClassificationRunRepository runRepository;
    private final RecipeClassificationProposalRepository proposalRepository;
    private final ClassificationProvenance provenance;
    private final Map<ClassificationKind, RecipeClassifier<?>> classifiers;
    private final Clock clock;

    public RecipeClassificationService(RecipeRepository recipeRepository, RecipeClassificationRunRepository runRepository,
            RecipeClassificationProposalRepository proposalRepository, ClassificationProvenance provenance,
            List<RecipeClassifier<?>> classifiers, Clock clock) {
        this.recipeRepository = recipeRepository;
        this.runRepository = runRepository;
        this.proposalRepository = proposalRepository;
        this.provenance = provenance;
        this.classifiers = classifiers.stream()
                .collect(Collectors.toMap(classifier -> classifier.attribute().kind(), Function.identity()));
        this.clock = clock;
    }

    public List<RecipeClassificationRun> getRuns() {
        return runRepository.findAllByOrderByIdDesc();
    }

    public RecipeClassificationRun getRun(Long id) throws ElementNotFound {
        return runRepository.findById(id).orElseThrow(ElementNotFound::new);
    }

    public List<RecipeClassificationProposal> getProposals(Long runId) throws ElementNotFound {
        return proposalRepository.findAllOf(getRun(runId));
    }

    /** Reads every recipe in scope and writes down what it would make of it. Changes no recipe. */
    public RecipeClassificationRun preview(ClassificationKind kind, RecipeClassificationRun.Scope scope,
            CookpalUser startedBy) throws ElementNotFound {
        var classifier = classifierFor(kind);
        var run = runRepository.save(RecipeClassificationRun.builder()
                .kind(kind)
                .scope(scope)
                .basis(classifier.basis())
                .build());
        run.setStartedBy(startedBy);

        var marks = provenance.allOf(kind);
        var proposals = recipeRepository.findAllWithIngredients().stream()
                .filter(recipe -> isInScope(classifier.attribute().valueOf(recipe), marks.get(recipe.getId()), scope))
                .map(recipe -> proposalFor(classifier, run, recipe, marks.get(recipe.getId())))
                .toList();
        proposalRepository.saveAll(proposals);

        run.setProposalCount((int) proposals.stream().filter(RecipeClassificationProposal::isDecidable).count());
        run.setUnreadableCount(proposals.size() - run.getProposalCount());
        log.info("Classification run {} ({}) previewed {} recipes, {} decidable", run.getId(), kind, proposals.size(),
                run.getProposalCount());
        return runRepository.save(run);
    }

    /** Skipping is the run's own decision about a recipe it cannot read, never a reviewer's. */
    public List<RecipeClassificationProposal> decide(Long runId, List<Long> proposalIds,
            RecipeClassificationProposal.Decision decision) throws ApiException {
        if (decision == RecipeClassificationProposal.Decision.SKIPPED) {
            throw new ApiException(ApiErrorCode.VALIDATION_FAILED, "A proposal is accepted, rejected or left pending");
        }
        var run = requirePreviewed(runId);
        var proposals = proposalRepository.findAllByRunAndIdIn(run, proposalIds).stream()
                .filter(RecipeClassificationProposal::isDecidable)
                .toList();
        proposals.forEach(proposal -> proposal.setDecision(decision));
        return proposalRepository.saveAll(proposals);
    }

    /** Writes the accepted proposals onto their recipes. A recipe changed since the preview is skipped. */
    public RecipeClassificationRun apply(Long runId) throws ApiException {
        var run = requirePreviewed(runId);
        var attribute = classifierFor(run.getKind()).attribute();
        var accepted = ReviewedRuns.requireAnyAccepted(run,
                proposalRepository.findAllByRunAndDecision(run, RecipeClassificationProposal.Decision.ACCEPTED));
        var applied = 0;
        var skipped = 0;

        for (var proposal : accepted) {
            var recipe = proposal.getRecipe();
            if (hasChangedSincePreview(attribute, recipe, proposal)) {
                log.info("Skipping recipe {}: it changed since the preview", recipe.getId());
                proposal.setDecision(RecipeClassificationProposal.Decision.SKIPPED);
                proposalRepository.save(proposal);
                skipped++;
                continue;
            }
            attribute.writeEncoded(recipe, proposal.getProposedValue());
            provenance.attribute(recipe, run.getKind(), run);
            recipeRepository.save(recipe);
            applied++;
        }

        run.markApplied(clock.instant(), applied, skipped);
        log.info("Classification run {} applied to {} recipes, {} skipped as changed since the preview", run.getId(),
                applied, skipped);
        return runRepository.save(run);
    }

    /** Puts back what each recipe had, but only where this run is still the last to have written it. */
    public RecipeClassificationRun revert(Long runId) throws ApiException {
        var run = ReviewedRuns.require(getRun(runId), ReviewedRun.Status.APPLIED);
        var attribute = classifierFor(run.getKind()).attribute();
        for (var proposal : proposalRepository.findAllByRunAndDecision(run, RecipeClassificationProposal.Decision.ACCEPTED)) {
            var recipe = proposal.getRecipe();
            if (isSameRun(provenance.derivedBy(recipe, run.getKind()).orElse(null), run)) {
                attribute.writeEncoded(recipe, proposal.getPreviousValue());
                provenance.attribute(recipe, run.getKind(), proposal.getPreviousRun());
                recipeRepository.save(recipe);
            }
        }
        run.markReverted(clock.instant());
        return runRepository.save(run);
    }

    public RecipeClassificationRun discard(Long runId) throws ApiException {
        var run = requirePreviewed(runId);
        run.discard();
        return runRepository.save(run);
    }

    /** A person's value has no mark but does have a value, so neither scope reaches it. */
    private static boolean isInScope(Object value, DerivedClassification mark, RecipeClassificationRun.Scope scope) {
        return switch (scope) {
            case NEVER_CLASSIFIED -> value == null;
            case DERIVED_ONLY -> mark != null;
        };
    }

    private static <T> RecipeClassificationProposal proposalFor(RecipeClassifier<T> classifier,
            RecipeClassificationRun run, Recipe recipe, DerivedClassification mark) {
        var attribute = classifier.attribute();
        var reading = classifier.read(recipe);
        return RecipeClassificationProposal.builder()
                .run(run)
                .recipe(recipe)
                .previousValue(attribute.encodedValueOf(recipe))
                .previousRun(mark == null ? null : mark.getRun())
                .proposedValue(reading.value().map(attribute::encode).orElse(null))
                .decision(reading.value().isPresent() ?
                        RecipeClassificationProposal.Decision.PENDING :
                        RecipeClassificationProposal.Decision.SKIPPED)
                .reason(reading.reason())
                .build();
    }

    /** Somebody changed the value, or who decided it, between the preview and now; applying would step on them. */
    private boolean hasChangedSincePreview(ClassifiedAttribute<?> attribute, Recipe recipe,
            RecipeClassificationProposal proposal) {
        return !Objects.equals(attribute.encodedValueOf(recipe), proposal.getPreviousValue())
                || !isSameRun(provenance.derivedBy(recipe, attribute.kind()).orElse(null), proposal.getPreviousRun());
    }

    private static boolean isSameRun(RecipeClassificationRun left, RecipeClassificationRun right) {
        return Objects.equals(left == null ? null : left.getId(), right == null ? null : right.getId());
    }

    /** A kind this instance cannot classify - diets need nutrition estimation switched on - does not exist here. */
    private RecipeClassifier<?> classifierFor(ClassificationKind kind) throws ElementNotFound {
        var classifier = classifiers.get(kind);
        if (classifier == null) {
            throw new ElementNotFound();
        }
        return classifier;
    }

    private RecipeClassificationRun requirePreviewed(Long runId) throws ApiException {
        return ReviewedRuns.require(getRun(runId), ReviewedRun.Status.PREVIEWED);
    }
}
