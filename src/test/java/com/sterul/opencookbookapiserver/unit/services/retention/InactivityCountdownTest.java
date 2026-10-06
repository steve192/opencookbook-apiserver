package com.sterul.opencookbookapiserver.unit.services.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.services.retention.InactivityCountdown;
import com.sterul.opencookbookapiserver.services.retention.InactivityCountdown.Deletion;
import com.sterul.opencookbookapiserver.services.retention.InactivityCountdown.Notice;

class InactivityCountdownTest {

    private static final LocalDate LAST_ACTIVE = LocalDate.parse("2026-01-10");
    private static final LocalDate DUE = LocalDate.parse("2026-07-10");

    private final InactivityCountdown cut = new InactivityCountdown(6);

    @Test
    void nothingIsDueBeforeTheFirstNotice() {
        assertEquals(Optional.empty(), cut.next(active(0, null), DUE.minusDays(15), true));
    }

    @Test
    void theFirstNoticeComesTwoWeeksAheadAndStatesTheDueDate() {
        assertEquals(Optional.of(new Notice(1, DUE)), cut.next(active(0, null), DUE.minusDays(14), true));
    }

    @Test
    void aLateFirstNoticeStillGivesTwoWeeks() {
        var today = DUE.minusDays(3);

        assertEquals(Optional.of(new Notice(1, today.plusDays(14))), cut.next(active(0, null), today, true));
    }

    @Test
    void theSecondNoticeComesAWeekAheadAndAWeekAfterTheFirst() {
        var account = active(1, DUE.minusDays(14));

        assertEquals(Optional.empty(), cut.next(account, DUE.minusDays(8), true));
        assertEquals(Optional.of(new Notice(2, DUE)), cut.next(account, DUE.minusDays(7), true));
    }

    @Test
    void afterALateFirstNoticeTheSecondKeepsTheDateTheFirstStated() {
        var firstNotice = DUE.minusDays(3);
        var account = active(1, firstNotice);

        assertEquals(Optional.empty(), cut.next(account, firstNotice.plusDays(6), true));
        assertEquals(Optional.of(new Notice(2, firstNotice.plusDays(14))),
                cut.next(account, firstNotice.plusDays(7), true));
    }

    @Test
    void theDeletionComesOnTheDueDateAndAWeekAfterTheSecondNotice() {
        assertEquals(Optional.empty(), cut.next(active(2, DUE.minusDays(7)), DUE.minusDays(1), true));
        assertEquals(Optional.of(new Deletion()), cut.next(active(2, DUE.minusDays(7)), DUE, true));

        var lateSecondNotice = DUE.minusDays(2);
        assertEquals(Optional.empty(), cut.next(active(2, lateSecondNotice), DUE, true));
        assertEquals(Optional.of(new Deletion()), cut.next(active(2, lateSecondNotice), lateSecondNotice.plusDays(7), true));
    }

    @Test
    void withoutMailAnActivatedAccountIsKept() {
        assertEquals(Optional.empty(), cut.next(active(0, null), DUE.plusYears(1), false));
        assertEquals(Optional.empty(), cut.next(active(2, DUE.minusDays(7)), DUE.plusYears(1), false));
    }

    @Test
    void anAccountThatCannotSignInIsDeletedOnItsDueDateWithoutNotices() {
        var locked = active(0, null);
        locked.setActivated(false);

        assertEquals(Optional.empty(), cut.next(locked, DUE.minusDays(1), true));
        assertEquals(Optional.of(new Deletion()), cut.next(locked, DUE, true));
        assertEquals(Optional.of(new Deletion()), cut.next(locked, DUE, false));
    }

    @Test
    void anAccountNeverUsedCountsFromItsCreation() {
        var account = active(0, null);
        account.setLastActiveAt(null);
        account.setCreatedOn(startOf(LAST_ACTIVE.plusDays(1)));

        assertEquals(Optional.empty(), cut.next(account, DUE.minusDays(14), true));
        assertEquals(Optional.of(new Notice(1, DUE.plusDays(1))), cut.next(account, DUE.minusDays(13), true));
    }

    @Test
    void anAccountDueAtTheEndOfAShortMonthIsACandidate() {
        var account = active(0, null);
        account.setLastActiveAt(Instant.parse("2026-08-31T23:00:00Z"));
        var today = LocalDate.parse("2027-02-14");

        assertEquals(Optional.of(new Notice(1, LocalDate.parse("2027-02-28"))), cut.next(account, today, true));
        assertTrue(account.getLastActiveAt().isBefore(cut.dueCandidatesActiveBefore(today)));
    }

    private static CookpalUser active(int noticesSent, LocalDate lastNotice) {
        var account = new CookpalUser();
        account.setActivated(true);
        account.setCreatedOn(startOf(LAST_ACTIVE.minusYears(1)));
        account.setLastActiveAt(startOf(LAST_ACTIVE).plusSeconds(3600 * 15));
        account.setInactivityNotices(noticesSent);
        account.setLastInactivityNoticeAt(lastNotice == null ? null : startOf(lastNotice).plusSeconds(3600 * 4));
        return account;
    }

    private static Instant startOf(LocalDate day) {
        return day.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
