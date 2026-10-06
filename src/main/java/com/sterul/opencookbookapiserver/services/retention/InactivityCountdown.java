package com.sterul.opencookbookapiserver.services.retention;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

/**
 * What is due next for an account on its way to deletion for disuse.
 *
 * An activated account is warned twice and is only deleted after both notices went out, each a week after the one
 * before. A late notice therefore moves the deletion instead of shortening the warning. An account that cannot sign
 * in is deleted without notices.
 */
public class InactivityCountdown {

    /** For each notice and then the deletion: how many days before the due date it comes. */
    private static final List<Integer> DAYS_AHEAD = List.of(14, 7, 0);
    private static final int NOTICES = DAYS_AHEAD.size() - 1;
    /** Months clamp to a month's last day (31 August plus 6 months is 28 February); these keep such accounts in. */
    private static final long SPARE_DAYS = 4;

    public sealed interface Step permits Notice, Deletion {
    }

    /** @param number 1 for the first notice */
    public record Notice(int number, LocalDate deletionOn) implements Step {
    }

    public record Deletion() implements Step {
    }

    private final Period inactivePeriod;

    public InactivityCountdown(int inactiveMonths) {
        this.inactivePeriod = Period.ofMonths(inactiveMonths);
    }

    /** Only accounts last active before this can have a step due today. */
    public Instant dueCandidatesActiveBefore(LocalDate today) {
        return today.plusDays(DAYS_AHEAD.getFirst() + SPARE_DAYS).minus(inactivePeriod)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public Optional<Step> next(CookpalUser account, LocalDate today, boolean noticesCanBeSent) {
        var deletionDue = dateOf(account.lastActiveOrCreatedAt()).plus(inactivePeriod);
        if (!account.isActivated()) {
            return today.isBefore(deletionDue) ? Optional.empty() : Optional.of(new Deletion());
        }
        var sent = account.getInactivityNotices();
        var daysAhead = DAYS_AHEAD.get(sent);
        if (!noticesCanBeSent || today.isBefore(deletionDue.minusDays(daysAhead))
                || today.isBefore(spacedFromPreviousNotice(account))) {
            return Optional.empty();
        }
        if (sent == NOTICES) {
            return Optional.of(new Deletion());
        }
        var earliest = today.plusDays(daysAhead);
        return Optional.of(new Notice(sent + 1, deletionDue.isAfter(earliest) ? deletionDue : earliest));
    }

    /** The next step is as many days after the previous notice as their days ahead differ. */
    private static LocalDate spacedFromPreviousNotice(CookpalUser account) {
        var sent = account.getInactivityNotices();
        if (sent == 0) {
            return LocalDate.MIN;
        }
        return dateOf(account.getLastInactivityNoticeAt()).plusDays(DAYS_AHEAD.get(sent - 1) - (long) DAYS_AHEAD.get(sent));
    }

    private static LocalDate dateOf(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }
}
