package com.sterul.opencookbookapiserver.unit.services.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.ml.MlJob;
import com.sterul.opencookbookapiserver.entities.ml.MlJobStatus;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.repositories.MlJobRepository;
import com.sterul.opencookbookapiserver.services.ml.MlAvailabilityService;
import com.sterul.opencookbookapiserver.services.ml.MlJobService;
import com.sterul.opencookbookapiserver.services.ml.MlSubmitterIds;
import com.sterul.opencookbookapiserver.services.ml.MlSubsystemException;
import com.sterul.opencookbookapiserver.services.ml.MlSubsystemProxy;
import com.sterul.opencookbookapiserver.services.ml.MlUnavailableException;
import com.sterul.opencookbookapiserver.services.ml.MlUserQuotaExceededException;
import com.sterul.opencookbookapiserver.services.ml.RecipeOcrPayload;
import com.sterul.opencookbookapiserver.unit.MovableClock;

/**
 * The cookpal side of a job, with the subsystem replaced by a mock.
 *
 * What matters here is what happens when the other service misbehaves: the job must never be
 * left in a state the app can wait on for ever, and a subsystem that is merely unreachable for
 * a moment must not turn into a pile of failed jobs.
 */
class MlJobServiceTest {

    private static final String REMOTE_ID = "b6f4e2a0-0000-0000-0000-000000000001";

    private final MovableClock clock = new MovableClock(Instant.parse("2026-09-02T10:00:00Z"));
    private final MlJobRepository repository = mock(MlJobRepository.class);
    private final MlSubsystemProxy proxy = mock(MlSubsystemProxy.class);
    private final MlAvailabilityService availability = mock(MlAvailabilityService.class);
    private final MlSubmitterIds submitterIds = mock(MlSubmitterIds.class);
    private final OpencookbookConfiguration configuration = new OpencookbookConfiguration();
    private final CookpalUser owner = new CookpalUser();

    private MlJobService cut;

    @BeforeEach
    void setUp() throws MlUnavailableException {
        configuration.getMl().setServiceUrl("http://ml:8000");
        configuration.getMl().setJobTimeoutSeconds(300);
        configuration.getMl().getRecipeOcr().setJobsPerUserPerDay(2);
        when(repository.save(any(MlJob.class))).thenAnswer(call -> call.getArgument(0));
        when(submitterIds.of(owner)).thenReturn("submitter-hash");
        cut = new MlJobService(repository, proxy, availability, submitterIds, configuration, clock);
    }

    @Test
    void anAllowanceResetLeavesTheScansAndStopsThemCounting() {
        // The record of what was run should survive an operator granting an exception.
        var counted = List.of(MlJob.builder().id("a").owner(owner).build(),
                MlJob.builder().id("b").owner(owner).build());
        when(repository.findUsageSince(eq(owner), any()))
                .thenReturn(counted);

        var reset = cut.resetQuota(owner);

        assertEquals(2, reset);
        counted.forEach(job -> assertFalse(job.isCountsTowardsQuota()));
        verify(repository).saveAll(counted);
        verify(repository, never()).delete(any());
    }

    @Test
    void aResetAllowanceLetsSomebodyScanAgain() throws Exception {
        // The point of the feature: the count is what has to change, not just a flag.
        when(repository.countUsageSince(eq(owner), any()))
                .thenReturn(2L, 0L);
        assertThrows(MlUserQuotaExceededException.class,
                () -> cut.submitRecipeOcr(owner, List.of(), new RecipeOcrPayload(), false));
        when(repository.findUsageSince(eq(owner), any()))
                .thenReturn(List.of(MlJob.builder().id("a").owner(owner).build()));
        when(submitted()).thenReturn(REMOTE_ID);

        cut.resetQuota(owner);

        var job = cut.submitRecipeOcr(owner, List.of(), new RecipeOcrPayload(), false);
        assertEquals(REMOTE_ID, job.getRemoteJobId());
    }

    @Test
    void aScanCountsTowardsTheAllowanceUnlessItHasBeenReset() {
        assertTrue(MlJob.builder().id("a").build().isCountsTowardsQuota());
    }

    @Test
    void aSubmittedJobRemembersTheSubsystemsIdForIt() throws Exception {
        when(submitted()).thenReturn(REMOTE_ID);

        var job = cut.submitRecipeOcr(owner, List.of(), new RecipeOcrPayload(), false);

        assertEquals(REMOTE_ID, job.getRemoteJobId());
        assertEquals(MlJobStatus.QUEUED, job.getStatus());
        verify(availability).reportReachable();
    }

    @Test
    void aUserWhoHasUsedTodaysAllowanceIsRefusedBeforeAnythingIsUploaded() throws Exception {
        when(repository.countUsageSince(eq(owner), any()))
                .thenReturn(2L);

        assertThrows(MlUserQuotaExceededException.class,
                () -> cut.submitRecipeOcr(owner, List.of(), new RecipeOcrPayload(), false));

        verify(proxy, never()).submitRecipeOcr(anyString(), any(RecipeOcrPayload.class),
                anyList(), anyBoolean(), anyString());
        verify(repository, never()).save(any(MlJob.class));
    }

    @Test
    void theAllowanceIsCountedFromTheStartOfTheDay() throws Exception {
        when(submitted()).thenReturn(REMOTE_ID);

        cut.submitRecipeOcr(owner, List.of(), new RecipeOcrPayload(), false);

        verify(repository).countUsageSince(owner,
                Instant.parse("2026-09-02T00:00:00Z"));
    }

    @Test
    void anAllowanceOfZeroMeansUnlimited() throws Exception {
        configuration.getMl().getRecipeOcr().setJobsPerUserPerDay(0);
        when(repository.countUsageSince(eq(owner), any())).thenReturn(9999L);
        when(submitted()).thenReturn(REMOTE_ID);

        assertEquals(REMOTE_ID,
                cut.submitRecipeOcr(owner, List.of(), new RecipeOcrPayload(), false)
                        .getRemoteJobId());
    }

    @Test
    void aRefusedSubmissionLeavesAFailedJobRatherThanOneThatWaitsForEver() throws Exception {
        when(submitted())
                .thenThrow(new MlSubsystemException("ATTACHMENT_TOO_LARGE", "too big", false));

        assertThrows(MlSubsystemException.class,
                () -> cut.submitRecipeOcr(owner, List.of(), new RecipeOcrPayload(), false));

        var saved = savedJob();
        assertEquals(MlJobStatus.FAILED, saved.getStatus());
        assertEquals("ATTACHMENT_TOO_LARGE", saved.getErrorCode());
        assertEquals(clock.instant(), saved.getFinishedAt());
    }

    @Test
    void aScanTheSubsystemNeverTookOnDoesNotCostSomebodyTheirDay() throws Exception {
        // The allowance rations the instance's allowance with the subsystem, and a submission
        // that never arrived spent none of it.
        when(submitted()).thenThrow(new MlUnavailableException("ML_UNREACHABLE", "down"));

        assertThrows(MlUnavailableException.class,
                () -> cut.submitRecipeOcr(owner, List.of(), new RecipeOcrPayload(), false));

        assertFalse(savedJob().isCountsTowardsQuota());
    }

    @Test
    void aScanTheSubsystemDidTakeOnAndThenFailedStillCounts() throws Exception {
        // It reached the worker, so it cost the instance something.
        var job = queuedJob();
        when(repository.findByStatusIn(anyList())).thenReturn(List.of(job));
        when(proxy.fetch(REMOTE_ID)).thenReturn(new MlSubsystemProxy.MlJobState(
                REMOTE_ID, MlJobStatus.FAILED, null, "OCR_NO_TEXT_FOUND", "nothing", false, null));

        cut.refreshUnfinishedJobs();

        assertTrue(job.isCountsTowardsQuota());
    }

    @Test
    void anUnreachableSubsystemIsRememberedSoTheFeatureCanSwitchItselfOff() throws Exception {
        when(submitted()).thenThrow(new MlUnavailableException("ML_UNREACHABLE", "down"));

        assertThrows(MlUnavailableException.class,
                () -> cut.submitRecipeOcr(owner, List.of(), new RecipeOcrPayload(), false));

        verify(availability).reportUnreachable();
    }

    @Test
    void aFinishedJobTakesOnTheSubsystemsResult() throws Exception {
        var job = queuedJob();
        when(repository.findByStatusIn(anyList())).thenReturn(List.of(job));
        when(proxy.fetch(REMOTE_ID)).thenReturn(new MlSubsystemProxy.MlJobState(
                REMOTE_ID, MlJobStatus.COMPLETED, "{\"title\":{}}", null, null, false, null));

        cut.refreshUnfinishedJobs();

        assertEquals(MlJobStatus.COMPLETED, job.getStatus());
        assertEquals("{\"title\":{}}", job.getResult());
        assertEquals(clock.instant(), job.getFinishedAt());
    }

    @Test
    void aWaitingJobRemembersHowFarBackInTheQueueItIs() throws Exception {
        // So that waiting can say something better than "please wait".
        var job = queuedJob();
        when(repository.findByStatusIn(anyList())).thenReturn(List.of(job));
        when(proxy.fetch(REMOTE_ID)).thenReturn(new MlSubsystemProxy.MlJobState(
                REMOTE_ID, MlJobStatus.QUEUED, null, null, null, false, 4));

        cut.refreshUnfinishedJobs();

        assertEquals(4, job.getQueuePosition());
        assertNull(job.getFinishedAt());
    }

    @Test
    void aJobThatIsRunningNoLongerClaimsAQueuePosition() throws Exception {
        var job = queuedJob();
        job.setQueuePosition(4);
        when(repository.findByStatusIn(anyList())).thenReturn(List.of(job));
        when(proxy.fetch(REMOTE_ID)).thenReturn(new MlSubsystemProxy.MlJobState(
                REMOTE_ID, MlJobStatus.PROCESSING, null, null, null, false, null));

        cut.refreshUnfinishedJobs();

        assertNull(job.getQueuePosition());
    }

    @Test
    void aBrieflyUnreachableSubsystemDoesNotFailTheJob() throws Exception {
        var job = queuedJob();
        when(repository.findByStatusIn(anyList())).thenReturn(List.of(job));
        when(proxy.fetch(REMOTE_ID))
                .thenThrow(new MlUnavailableException("ML_UNREACHABLE", "down"));

        cut.refreshUnfinishedJobs();

        assertEquals(MlJobStatus.QUEUED, job.getStatus());
        assertNull(job.getFinishedAt());
        verify(availability).reportUnreachable();
    }

    @Test
    void aJobTheSubsystemNeverFinishesIsEventuallyAbandoned() throws Exception {
        var job = queuedJob();
        when(repository.findByStatusIn(anyList())).thenReturn(List.of(job));
        clock.advanceBy(Duration.ofSeconds(301));

        cut.refreshUnfinishedJobs();

        assertEquals(MlJobStatus.FAILED, job.getStatus());
        assertEquals("ML_TIMEOUT", job.getErrorCode());
        verify(proxy, never()).fetch(anyString());
    }

    @Test
    void aSubmissionThatNeverGotAnIdIsNotAskedAbout() throws Exception {
        var job = queuedJob();
        job.setRemoteJobId(null);
        when(repository.findByStatusIn(anyList())).thenReturn(List.of(job));

        cut.refreshUnfinishedJobs();

        verify(proxy, never()).fetch(anyString());
        assertEquals(MlJobStatus.QUEUED, job.getStatus());
    }

    @Test
    void cancellingTellsTheSubsystemAndClosesTheJob() throws Exception {
        var job = queuedJob();
        when(repository.findByIdAndOwner("job-1", owner)).thenReturn(Optional.of(job));

        cut.cancel(owner, "job-1");

        verify(proxy).cancel(REMOTE_ID);
        assertEquals(MlJobStatus.CANCELLED, job.getStatus());
    }

    @Test
    void cancellingSomethingAlreadyFinishedDoesNothing() throws Exception {
        var job = queuedJob();
        job.setStatus(MlJobStatus.COMPLETED);
        when(repository.findByIdAndOwner("job-1", owner)).thenReturn(Optional.of(job));

        cut.cancel(owner, "job-1");

        verify(proxy, never()).cancel(anyString());
        assertEquals(MlJobStatus.COMPLETED, job.getStatus());
    }

    @Test
    void correctingAScanReplacesItsResultWithoutStartingAnotherJob() throws Exception {
        var job = queuedJob();
        job.setStatus(MlJobStatus.COMPLETED);
        when(repository.findByIdAndOwner("job-1", owner)).thenReturn(Optional.of(job));
        when(proxy.refine(eq(REMOTE_ID), anyMap())).thenReturn(new MlSubsystemProxy.MlJobState(
                REMOTE_ID, MlJobStatus.COMPLETED, "{\"corrected\":true}", null, null, false, null));

        var refined = cut.refine(owner, "job-1",
                Map.of("blocks", Collections.singletonMap("steps", null)));

        assertEquals("{\"corrected\":true}", refined.getResult());
        verify(proxy, never()).submitRecipeOcr(anyString(), any(RecipeOcrPayload.class),
                anyList(), anyBoolean(), anyString());
    }

    @Test
    void aScanThatNeverReachedTheSubsystemCannotBeCorrected() throws Exception {
        var job = queuedJob();
        job.setRemoteJobId(null);
        when(repository.findByIdAndOwner("job-1", owner)).thenReturn(Optional.of(job));

        assertThrows(ElementNotFound.class, () -> cut.refine(owner, "job-1", Map.of()));
    }

    @Test
    void withdrawingConsentIsForwardedUnderTheOpaqueId() throws Exception {
        when(proxy.deleteTrainingData("submitter-hash")).thenReturn(3);

        assertEquals(3, cut.deleteTrainingData(owner));
    }

    /** The submission call, which every one of these tests stubs the same way. */
    private String submitted() throws MlSubsystemException {
        return proxy.submitRecipeOcr(anyString(), any(RecipeOcrPayload.class), anyList(),
                anyBoolean(), anyString());
    }

    private MlJob queuedJob() {
        var job = MlJob.builder()
                .id("job-1")
                .owner(owner)
                .jobType(MlJobService.RECIPE_OCR_JOB_TYPE)
                .status(MlJobStatus.QUEUED)
                .remoteJobId(REMOTE_ID)
                .build();
        job.setCreatedOn(clock.instant());
        return job;
    }

    private MlJob savedJob() {
        var captor = ArgumentCaptor.forClass(MlJob.class);
        verify(repository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }
}
