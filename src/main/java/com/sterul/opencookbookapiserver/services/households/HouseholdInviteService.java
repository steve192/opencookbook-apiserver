package com.sterul.opencookbookapiserver.services.households;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.household.Household;
import com.sterul.opencookbookapiserver.entities.household.HouseholdInvite;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.repositories.HouseholdInviteRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import lombok.extern.slf4j.Slf4j;

/** Invite links: single use, unguessable, expiring, and indistinguishable when they fail. */
@Service
@Slf4j
@Transactional
public class HouseholdInviteService {

    private final HouseholdInviteRepository inviteRepository;
    private final HouseholdMembershipService memberships;
    private final OpencookbookConfiguration configuration;
    private final Clock clock;

    public HouseholdInviteService(HouseholdInviteRepository inviteRepository,
            HouseholdMembershipService memberships, OpencookbookConfiguration configuration, Clock clock) {
        this.inviteRepository = inviteRepository;
        this.memberships = memberships;
        this.configuration = configuration;
        this.clock = clock;
    }

    public HouseholdInvite create(String householdId, CookpalUser creator) {
        var household = memberships.requireMembership(householdId, creator).getHousehold();
        var live = inviteRepository.countByHouseholdIdAndExpiresAtAfter(householdId, clock.instant());
        if (live >= configuration.getHouseholds().getMaxLiveInvites()) {
            throw new ApiException(ApiErrorCode.TOO_MANY_INVITES, "Too many open invites");
        }

        log.info("User {} created an invite for household {}", creator.getUserId(), householdId);
        return inviteRepository.save(HouseholdInvite.builder()
                .household(household)
                .createdBy(creator)
                .expiresAt(clock.instant().plus(Duration.ofDays(configuration.getHouseholds().getInviteValidityDays())))
                .build());
    }

    public List<HouseholdInvite> liveInvitesOf(String householdId, CookpalUser requester) {
        memberships.requireMembership(householdId, requester);
        return inviteRepository.findAllByHouseholdIdAndExpiresAtAfterOrderByCreatedOnDesc(householdId, clock.instant());
    }

    public void revoke(String householdId, String inviteId, CookpalUser requester) {
        memberships.requireMembership(householdId, requester);
        var invite = inviteRepository.findById(inviteId)
                .filter(candidate -> candidate.getHousehold().getId().equals(householdId))
                .orElseThrow(ElementNotFound::new);
        log.info("User {} revoked invite of household {}", requester.getUserId(), householdId);
        inviteRepository.delete(invite);
    }

    public Household preview(String token) {
        return resolve(token).getHousehold();
    }

    public Household accept(String token, CookpalUser user, boolean shareRecipes) {
        var invite = resolve(token);
        var household = invite.getHousehold();
        // Joined first: a refused join throws before the invite is used up.
        memberships.join(household, user, shareRecipes);
        inviteRepository.delete(invite);
        return household;
    }

    public int deleteExpiredInvites() {
        return inviteRepository.deleteByExpiresAtBefore(clock.instant());
    }

    /** One answer for expired, revoked and never-existed alike. */
    private HouseholdInvite resolve(String token) {
        return inviteRepository.findById(token)
                .filter(invite -> !invite.hasExpired(clock.instant()))
                .orElseThrow(() -> new ApiException(ApiErrorCode.INVITE_INVALID, "Invite is not valid"));
    }
}
