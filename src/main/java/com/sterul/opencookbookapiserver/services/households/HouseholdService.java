package com.sterul.opencookbookapiserver.services.households;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;
import com.sterul.opencookbookapiserver.entities.household.HouseholdMembership;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.HouseholdRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import lombok.extern.slf4j.Slf4j;

/**
 * The household itself, from its start to its end. Who is in it and what they share is
 * {@link HouseholdMembershipService}.
 */
@Service
@Slf4j
@Transactional
public class HouseholdService {

    private final HouseholdRepository householdRepository;
    private final HouseholdMembershipService memberships;
    private final ApplicationEventPublisher events;

    public HouseholdService(HouseholdRepository householdRepository, HouseholdMembershipService memberships,
            ApplicationEventPublisher events) {
        this.householdRepository = householdRepository;
        this.memberships = memberships;
        this.events = events;
    }

    public Household create(String name, CookpalUser creator, boolean shareRecipes) throws ApiException {
        var household = householdRepository.save(Household.builder().name(name.strip()).build());
        log.info("User {} created household {}", creator.getUserId(), household.getId());
        // Through join, so the creator counts against the caps too.
        memberships.join(household, creator, shareRecipes);
        return household;
    }

    public Household rename(String householdId, String name, CookpalUser requester) throws ElementNotFound {
        var household = memberships.requireMembership(householdId, requester).getHousehold();
        household.setName(name.strip());
        return householdRepository.save(household);
    }

    /** Empty once the household has ended. */
    @Transactional(readOnly = true)
    public Optional<Household> find(String householdId) {
        return householdRepository.findById(householdId);
    }

    @Transactional(readOnly = true)
    public List<Household> getAllForAdministration() {
        return householdRepository.findAllForAdministration();
    }

    /** Leaving and being removed are the same; any member may remove any member. */
    public void removeMember(String householdId, Long memberUserId, CookpalUser requester) throws ElementNotFound {
        memberships.requireMembership(householdId, requester);
        var membership = memberships.membershipOf(householdId, memberUserId);
        log.info("User {} is removing user {} from household {}", requester.getUserId(), memberUserId, householdId);
        leave(membership);
    }

    /** For an account being deleted. */
    public void leaveAll(CookpalUser user) {
        memberships.householdsOf(user).forEach(this::leave);
    }

    public void dissolveAsAdministrator(String householdId) throws ElementNotFound {
        var household = householdRepository.findById(householdId).orElseThrow(ElementNotFound::new);
        log.info("Administrator is dissolving household {}", householdId);
        memberships.membersOf(householdId).forEach(memberships::remove);
        end(household);
    }

    /** The last one out ends it. */
    private void leave(HouseholdMembership membership) {
        var household = membership.getHousehold();
        memberships.remove(membership);
        if (memberships.memberCountOf(household.getId()) == 0) {
            log.info("Household {} ended with its last member", household.getId());
            end(household);
        }
    }

    /** Announced first: the database cannot cascade into the meals of a weekplan day. */
    private void end(Household household) {
        events.publishEvent(new HouseholdEnding(household.getId()));
        householdRepository.delete(household);
    }
}
