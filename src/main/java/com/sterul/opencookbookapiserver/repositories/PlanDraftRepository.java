package com.sterul.opencookbookapiserver.repositories;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;
import com.sterul.opencookbookapiserver.entities.planning.PlanDraft;
import com.sterul.opencookbookapiserver.entities.planning.PlanningProfile;
import com.sterul.opencookbookapiserver.entities.PlanScope;

public interface PlanDraftRepository extends JpaRepository<PlanDraft, Long> {

    /** Somebody else's draft is simply not found, so ids cannot be probed. */
    default Optional<PlanDraft> findIn(Long id, PlanScope scope) {
        return findByIdAndOwnerAndHousehold(id, scope.owner(), scope.household());
    }

    /** A list, because drafts made before one-open-draft-per-week may overlap. */
    default List<PlanDraft> findOpenFor(LocalDate startDate, PlanScope scope) {
        return findAllByOwnerAndHouseholdAndStartDateAndStatus(scope.owner(), scope.household(), startDate,
                PlanDraft.Status.DRAFT);
    }

    default List<PlanDraft> findOpenIn(PlanScope scope) {
        return findAllByOwnerAndHouseholdAndStatus(scope.owner(), scope.household(), PlanDraft.Status.DRAFT);
    }

    Optional<PlanDraft> findByIdAndOwnerAndHousehold(Long id, CookpalUser owner, Household household);

    List<PlanDraft> findAllByOwnerAndHouseholdAndStartDateAndStatus(CookpalUser owner, Household household,
            LocalDate startDate, PlanDraft.Status status);

    List<PlanDraft> findAllByOwnerAndHouseholdAndStatus(CookpalUser owner, Household household,
            PlanDraft.Status status);

    List<PlanDraft> findAllByProfileAndStatus(PlanningProfile profile, PlanDraft.Status status);
}
