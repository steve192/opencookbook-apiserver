package com.sterul.opencookbookapiserver.services.households;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;
import com.sterul.opencookbookapiserver.entities.household.HouseholdMembership;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import lombok.extern.slf4j.Slf4j;

/**
 * Who is in a household and whose cookbook is in it. Every membership change runs through here;
 * what happens to the household when one ends is {@link HouseholdService}'s.
 */
@Service
@Slf4j
@Transactional
public class HouseholdMembershipService {

    private final HouseholdMembershipRepository membershipRepository;
    private final OpencookbookConfiguration configuration;
    private final ApplicationEventPublisher events;

    public HouseholdMembershipService(HouseholdMembershipRepository membershipRepository,
            OpencookbookConfiguration configuration, ApplicationEventPublisher events) {
        this.membershipRepository = membershipRepository;
        this.configuration = configuration;
        this.events = events;
    }

    /** "Not found" rather than "not allowed", so a household cannot be shown to exist by asking. */
    public HouseholdMembership requireMembership(String householdId, CookpalUser user) {
        return membershipRepository.findByHouseholdIdAndMember(householdId, user)
                .orElseThrow(ElementNotFound::new);
    }

    /** Somebody's membership, for acting on it; says nothing about who may. */
    public HouseholdMembership membershipOf(String householdId, Long memberUserId) {
        return membershipRepository.findByHouseholdIdAndMemberUserId(householdId, memberUserId)
                .orElseThrow(ElementNotFound::new);
    }

    @Transactional(readOnly = true)
    public List<HouseholdMembership> householdsOf(CookpalUser user) {
        return membershipRepository.findAllOfMember(user);
    }

    @Transactional(readOnly = true)
    public List<HouseholdMembership> membersOf(String householdId) {
        return membershipRepository.findAllInHousehold(householdId);
    }

    @Transactional(readOnly = true)
    public long memberCountOf(String householdId) {
        return membershipRepository.countByHouseholdId(householdId);
    }

    @Transactional(readOnly = true)
    public Set<Long> memberIdsOf(String householdId) {
        return membershipRepository.findMemberIds(householdId);
    }

    @Transactional(readOnly = true)
    public Set<Long> sharingMemberIdsOf(String householdId) {
        return membershipRepository.findSharingMemberIds(householdId);
    }

    public HouseholdMembership join(Household household, CookpalUser user, boolean shareRecipes) {
        if (membershipRepository.findByHouseholdIdAndMember(household.getId(), user).isPresent()) {
            throw new ApiException(ApiErrorCode.ALREADY_A_MEMBER, "Already a member");
        }
        var households = configuration.getHouseholds();
        if (membershipRepository.countByMember(user) >= households.getMaxPerUser()) {
            throw new ApiException(ApiErrorCode.TOO_MANY_HOUSEHOLDS, "Household limit reached");
        }
        if (membershipRepository.countByHouseholdId(household.getId()) >= households.getMaxMembers()) {
            throw new ApiException(ApiErrorCode.HOUSEHOLD_FULL, "Household is full");
        }

        log.info("User {} is joining household {}", user.getUserId(), household.getId());
        return membershipRepository.save(HouseholdMembership.builder()
                .household(household)
                .member(user)
                .shareRecipes(shareRecipes)
                .build());
    }

    public HouseholdMembership setSharing(String householdId, CookpalUser user, boolean shareRecipes) {
        var membership = requireMembership(householdId, user);
        if (membership.isShareRecipes() == shareRecipes) {
            return membership;
        }
        log.info("User {} is {} sharing their cookbook with household {}",
                user.getUserId(), shareRecipes ? "starting" : "stopping", householdId);
        membership.setShareRecipes(shareRecipes);
        var saved = membershipRepository.save(membership);
        if (!shareRecipes) {
            announceNarrowedAccess(householdId, othersIn(householdId, user));
        }
        return saved;
    }

    /** Whoever goes stops reading the others and, if they shared, the others stop reading them. */
    public void remove(HouseholdMembership membership) {
        var householdId = membership.getHousehold().getId();
        var member = membership.getMember();
        var readers = new HashSet<Long>(Set.of(member.getUserId()));
        if (membership.isShareRecipes()) {
            readers.addAll(othersIn(householdId, member));
        }
        membershipRepository.delete(membership);
        membershipRepository.flush();
        announceNarrowedAccess(householdId, readers);
    }

    private Set<Long> othersIn(String householdId, CookpalUser member) {
        var others = new HashSet<>(membershipRepository.findMemberIds(householdId));
        others.remove(member.getUserId());
        return others;
    }

    private void announceNarrowedAccess(String householdId, Set<Long> readerIds) {
        events.publishEvent(new ReadAccessNarrowed(householdId, readerIds));
    }
}
