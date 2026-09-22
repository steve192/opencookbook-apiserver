package com.sterul.opencookbookapiserver.services.planning;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.services.UserService;
import com.sterul.opencookbookapiserver.services.WeekplanService;
import com.sterul.opencookbookapiserver.services.households.HouseholdService;
import com.sterul.opencookbookapiserver.services.households.ReadAccessNarrowed;

import lombok.extern.slf4j.Slf4j;

/** Takes meals out of every plan whose readers lost access to them, weeks and open drafts alike. */
@Component
@Slf4j
public class UnreadableMealCleanup {

    private final WeekplanService weekplanService;
    private final PlanDraftService draftService;
    private final HouseholdService householdService;
    private final UserService userService;

    public UnreadableMealCleanup(WeekplanService weekplanService, PlanDraftService draftService,
            HouseholdService householdService, UserService userService) {
        this.weekplanService = weekplanService;
        this.draftService = draftService;
        this.householdService = householdService;
        this.userService = userService;
    }

    // After commit, so it needs its own transaction. A household or account deleted meanwhile is skipped.
    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeMealsThatBecameUnreadable(ReadAccessNarrowed narrowed) {
        householdService.find(narrowed.householdId()).map(PlanScope::of).ifPresent(this::clean);
        narrowed.readerIds().forEach(readerId ->
                userService.findUserById(readerId).map(PlanScope::of).ifPresent(this::clean));
    }

    private void clean(PlanScope plan) {
        var cleaned = weekplanService.removeUnreadableMeals(plan);
        if (cleaned > 0) {
            log.info("Removed unreadable meals from {} days", cleaned);
        }
        draftService.redrawUnreadableMeals(plan);
    }
}
