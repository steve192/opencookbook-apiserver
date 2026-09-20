package com.sterul.opencookbookapiserver.services.planning;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.sterul.opencookbookapiserver.entities.WeekplanDayRecipe;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.planning.PlanDraft;
import com.sterul.opencookbookapiserver.entities.planning.PlanDraftSlot;
import com.sterul.opencookbookapiserver.entities.planning.SlotKind;
import com.sterul.opencookbookapiserver.repositories.PlanDraftRepository;
import com.sterul.opencookbookapiserver.services.WeekplanService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.selection.Jitter;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Proposed weeks: generated from a profile, adjusted meal by meal, and only then accepted into the
 * weekplan. Accepting adds meals to the days and never removes what the cook planned by hand.
 */
@Service
@Slf4j
@Transactional
public class PlanDraftService {

    private final PlanDraftRepository draftRepository;
    private final PlanningProfileService profileService;
    private final PlanningPool planningPool;
    private final SlotLayout slotLayout;
    private final WeekplanGenerator generator;
    private final WeekplanService weekplanService;

    public PlanDraftService(PlanDraftRepository draftRepository, PlanningProfileService profileService,
            PlanningPool planningPool, SlotLayout slotLayout, WeekplanGenerator generator,
            WeekplanService weekplanService) {
        this.draftRepository = draftRepository;
        this.profileService = profileService;
        this.planningPool = planningPool;
        this.slotLayout = slotLayout;
        this.generator = generator;
        this.weekplanService = weekplanService;
    }

    /** @param skipped days the cook is not at home; no meals are planned for them at all */
    public PlanDraft generate(CookpalUser owner, Long profileId, LocalDate start, int days, Collection<LocalDate> skipped)
            throws ElementNotFound {
        var profile = profileService.getProfile(profileId, owner);
        var draft = PlanDraft.builder().owner(owner).profile(profile).startDate(start).days(days).seed(Jitter.newSeed()).build();
        for (var slot : slotLayout.layOut(start, days, skipped, profile.getMeals())) {
            draft.getSlots().add(PlanDraftSlot.builder().draft(draft).planDate(slot.date())
                    .mealType(slot.meal().getMealType()).kind(slot.kind()).build());
        }
        fill(draft, draft.getSlots(), Map.of());
        log.info("Generated draft of {} meals from {} for user {}", draft.getSlots().size(), start, owner.getUserId());
        return draftRepository.save(draft);
    }

    public PlanDraft getDraft(Long id, CookpalUser owner) throws ElementNotFound {
        return draftRepository.findByIdAndOwner(id, owner).orElseThrow(ElementNotFound::new);
    }

    /**
     * Something else for one meal: never the recipe it had, and chosen against the rest of the week.
     *
     * @param reason why the recipe was passed over, steering the replacement; null for just something else
     */
    public PlanDraft reroll(CookpalUser owner, Long draftId, Long slotId, RerollReason reason) throws ElementNotFound {
        var draft = requireOpen(draftId, owner);
        var slot = slotOf(draft, slotId);
        Map<PlanDraftSlot, Reroll> rerolls = slot.getRecipe() == null ? Map.of() :
                Map.of(slot, new Reroll(slot.getRecipe().getId(), reason));
        detachFromCooking(slot);
        slot.setKind(SlotKind.COOKED);
        fill(draft, withLeftoversOf(draft, slot), rerolls);
        return draft;
    }

    /** A locked meal stays as it is when the rest of the draft is drawn again. */
    public PlanDraft setLocked(CookpalUser owner, Long draftId, Long slotId, boolean locked) throws ElementNotFound {
        var draft = requireOpen(draftId, owner);
        slotOf(draft, slotId).setLocked(locked);
        return draft;
    }

    /**
     * Turns a meal into a gap for the cook to fill, or a gap back into a cooked meal. The one pattern a
     * weekly count cannot express - this Friday is takeaway - is exactly this one tap.
     */
    public PlanDraft toggleGap(CookpalUser owner, Long draftId, Long slotId) throws ElementNotFound {
        var draft = requireOpen(draftId, owner);
        var slot = slotOf(draft, slotId);
        var affected = withLeftoversOf(draft, slot);
        detachFromCooking(slot);
        slot.setKind(slot.getKind() == SlotKind.GAP ? SlotKind.COOKED : SlotKind.GAP);
        slot.setLocked(false);
        affected.stream().filter(other -> other != slot).forEach(leftover -> leftover.setKind(SlotKind.COOKED));
        fill(draft, affected, Map.of());
        return draft;
    }

    /** A new draw for every meal not locked, keeping locked meals and what they left over. */
    public PlanDraft rerollAll(CookpalUser owner, Long draftId) throws ElementNotFound {
        var draft = requireOpen(draftId, owner);
        draft.setSeed(Jitter.newSeed());
        var open = draft.getSlots().stream()
                .filter(slot -> !slot.isLocked())
                .filter(slot -> slot.getLeftoverOf() == null || !slot.getLeftoverOf().isLocked())
                .toList();
        open.stream().filter(slot -> slot.getKind() == SlotKind.LEFTOVER).forEach(slot -> slot.setKind(SlotKind.COOKED));
        fill(draft, open, Map.of());
        return draft;
    }

    /**
     * Writes the draft into the weekplan, adding its meals to each day in the order of the day. Gaps
     * are not written: the weekplan has no meal slots, so an empty entry would only be noise, and the
     * cook can add a free-text meal to any day there already.
     */
    public PlanDraft accept(CookpalUser owner, Long draftId) throws ElementNotFound {
        var draft = requireOpen(draftId, owner);
        var byDate = draft.getSlots().stream()
                .filter(slot -> slot.getRecipe() != null && slot.getKind() != SlotKind.GAP)
                .sorted(PlanDraftSlot.DAY_ORDER)
                .collect(Collectors.groupingBy(PlanDraftSlot::getPlanDate));
        byDate.forEach((date, meals) -> {
            var day = weekplanService.dayOf(date, owner);
            meals.forEach(meal -> day.getRecipes().add(WeekplanDayRecipe.builder()
                    .isSimpleRecipe(false).recipe(meal.getRecipe()).build()));
            weekplanService.updateWeekplanDay(day);
        });
        draft.setStatus(PlanDraft.Status.ACCEPTED);
        log.info("Accepted draft {} into the weekplan of user {}", draft.getId(), owner.getUserId());
        return draft;
    }

    public PlanDraft discard(CookpalUser owner, Long draftId) throws ElementNotFound {
        var draft = requireOpen(draftId, owner);
        draft.setStatus(PlanDraft.Status.DISCARDED);
        return draft;
    }

    private void fill(PlanDraft draft, Collection<PlanDraftSlot> toFill, Map<PlanDraftSlot, Reroll> rerolls) {
        var profile = draft.getProfile();
        var pool = planningPool.of(draft.getOwner(), profile);
        var recent = recentlyPlanned(draft.getOwner(), draft.getStartDate(), draft.getDays(), profile.getCooldownWeeks());
        generator.fill(draft.getSlots(), toFill, profile, pool, recent, rerolls, draft.getSeed());
    }

    /**
     * Recipes already in the weekplan within the cooldown before the draft, or anywhere in its own
     * period: a recipe the cook planned by hand for Thursday should not turn up again on Tuesday.
     */
    private Set<Long> recentlyPlanned(CookpalUser owner, LocalDate start, int days, int cooldownWeeks) {
        var from = start.minusWeeks(cooldownWeeks);
        return weekplanService.getWeekplanDaysBetweenTime(from, start.plusDays(days - 1L), owner).stream()
                .flatMap(day -> day.getRecipes().stream())
                .filter(planned -> !planned.isSimpleRecipe() && planned.getRecipe() != null)
                .map(planned -> planned.getRecipe().getId())
                .collect(Collectors.toSet());
    }

    private PlanDraft requireOpen(Long draftId, CookpalUser owner) throws ElementNotFound {
        var draft = getDraft(draftId, owner);
        if (!draft.isOpen()) {
            throw new ElementNotFound();
        }
        return draft;
    }

    private static PlanDraftSlot slotOf(PlanDraft draft, Long slotId) throws ElementNotFound {
        return draft.getSlots().stream().filter(slot -> slot.getId().equals(slotId)).findFirst()
                .orElseThrow(ElementNotFound::new);
    }

    /** A slot and the meals eaten from what it cooks; changing what is cooked changes them too. */
    private static List<PlanDraftSlot> withLeftoversOf(PlanDraft draft, PlanDraftSlot cooked) {
        var affected = new ArrayList<PlanDraftSlot>();
        affected.add(cooked);
        draft.getSlots().stream().filter(slot -> slot.getLeftoverOf() == cooked).forEach(affected::add);
        return affected;
    }

    /** A leftover taken out of its origin leaves the origin cooking for the household only. */
    private static void detachFromCooking(PlanDraftSlot slot) {
        var origin = slot.getLeftoverOf();
        if (origin != null) {
            origin.setServings(slot.getServings());
            slot.setLeftoverOf(null);
        }
    }
}
