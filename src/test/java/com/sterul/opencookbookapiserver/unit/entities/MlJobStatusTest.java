package com.sterul.opencookbookapiserver.unit.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sterul.opencookbookapiserver.entities.ml.MlJobStatus;

/**
 * Reading the state the subsystem reports.
 *
 * Both sides of this are ours, so the wire values are a fixed set rather than something to be
 * guessed at: they are the subsystem's Job.Status choices, stored in its database and put on
 * the wire unchanged.
 */
class MlJobStatusTest {

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource({
            "queued,    QUEUED",
            "processing, PROCESSING",
            "completed, COMPLETED",
            "failed,    FAILED",
            "cancelled, CANCELLED",
    })
    void everyStateTheSubsystemCanReportIsUnderstood(String reported, MlJobStatus expected) {
        assertEquals(expected, MlJobStatus.fromSubsystem(reported));
    }

    @Test
    void aStateFromANewerSubsystemIsTreatedAsStillRunning() {
        // Not finished: the poller asks again, and the job timeout is the backstop.
        assertEquals(MlJobStatus.PROCESSING, MlJobStatus.fromSubsystem("rebalancing"));
        assertFalse(MlJobStatus.fromSubsystem("rebalancing").isFinished());
    }

    @Test
    void nothingAtAllIsStillWaiting() {
        assertEquals(MlJobStatus.QUEUED, MlJobStatus.fromSubsystem(null));
    }

    @Test
    void onlyTheStatesNobodyPollsAgainCountAsFinished() {
        assertTrue(MlJobStatus.COMPLETED.isFinished());
        assertTrue(MlJobStatus.FAILED.isFinished());
        assertTrue(MlJobStatus.CANCELLED.isFinished());
        assertFalse(MlJobStatus.QUEUED.isFinished());
        assertFalse(MlJobStatus.PROCESSING.isFinished());
    }
}
