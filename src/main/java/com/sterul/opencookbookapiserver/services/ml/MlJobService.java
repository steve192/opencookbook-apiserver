package com.sterul.opencookbookapiserver.services.ml;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.configurations.ml.ConditionalOnMlConfigured;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.entities.ml.MlJob;
import com.sterul.opencookbookapiserver.entities.ml.MlJobStatus;
import com.sterul.opencookbookapiserver.repositories.MlJobRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import lombok.extern.slf4j.Slf4j;

/** Owns the cookpal side of a machine learning job: admitting it, tracking it, and cleaning up. */
@Service
@ConditionalOnMlConfigured
@Slf4j
public class MlJobService {

    public static final String RECIPE_OCR_JOB_TYPE = "recipe_ocr";

    private final MlJobRepository mlJobRepository;
    private final MlSubsystemProxy proxy;
    private final MlAvailabilityService availability;
    private final MlSubmitterIds submitterIds;
    private final OpencookbookConfiguration configuration;
    private final Clock clock;

    public MlJobService(MlJobRepository mlJobRepository, MlSubsystemProxy proxy,
            MlAvailabilityService availability, MlSubmitterIds submitterIds,
            OpencookbookConfiguration configuration, Clock clock) {
        this.mlJobRepository = mlJobRepository;
        this.proxy = proxy;
        this.availability = availability;
        this.submitterIds = submitterIds;
        this.configuration = configuration;
        this.clock = clock;
    }

    public MlJob submitRecipeOcr(CookpalUser owner, List<MultipartFile> images,
            RecipeOcrPayload payload, boolean trainingConsent) throws MlSubsystemException {

        requireRemainingQuota(owner);
        var job = createQueuedJob(owner, RECIPE_OCR_JOB_TYPE);

        try {
            var remoteJobId = proxy.submitRecipeOcr(RECIPE_OCR_JOB_TYPE, payload, images,
                    trainingConsent, submitterIds.of(owner));
            job.setRemoteJobId(remoteJobId);
            availability.reportReachable();
        } catch (MlUnavailableException e) {
            availability.reportUnreachable();
            failBeforeItWasAccepted(job, e);
            throw e;
        } catch (MlSubsystemException e) {
            failBeforeItWasAccepted(job, e);
            throw e;
        }
        return mlJobRepository.save(job);
    }

    /** The corners the app should start the crop from. */
    public MlSubsystemProxy.DetectedPage detectPageEdges(MultipartFile image)
            throws MlSubsystemException {
        var detected = proxy.detectPageEdges(image);
        availability.reportReachable();
        return detected;
    }

    public MlJob get(CookpalUser owner, String id) throws ElementNotFound {
        return mlJobRepository.findByIdAndOwner(id, owner).orElseThrow(ElementNotFound::new);
    }

    /** Apply what somebody marked as the ingredients and the steps, in the subsystem's shape. */
    public MlJob refine(CookpalUser owner, String id, Map<String, Object> corrections)
            throws ElementNotFound, MlSubsystemException {
        var job = get(owner, id);
        if (job.getRemoteJobId() == null) {
            throw new ElementNotFound();
        }

        var refined = proxy.refine(job.getRemoteJobId(), corrections);
        apply(job, refined);
        availability.reportReachable();
        return job;
    }

    /** Reports a refusing subsystem back, unlike {@link #resetJob}, which carries on. */
    public void cancel(CookpalUser owner, String id) throws ElementNotFound, MlSubsystemException {
        var job = get(owner, id);
        if (job.isFinished()) {
            return;
        }
        if (job.getRemoteJobId() != null) {
            proxy.cancel(job.getRemoteJobId());
        }
        markCancelled(job);
        mlJobRepository.save(job);
    }

    /** Newest first. A null owner or state means "any". */
    public List<MlJob> jobs(Long userId, MlJobStatus status) {
        return mlJobRepository.findMatching(userId, status);
    }

    public MlJob getAnyJob(String id) throws ElementNotFound {
        return mlJobRepository.findById(id).orElseThrow(ElementNotFound::new);
    }

    /** Stops a running scan and takes it off its owner's allowance, so they can try again. */
    public MlJob resetJob(String id) throws ElementNotFound {
        var job = getAnyJob(id);
        if (!job.isFinished()) {
            stopRemotely(job);
            markCancelled(job);
        }
        job.setCountsTowardsQuota(false);
        log.info("Admin: reset the scan {} of user {}", id, job.getOwner().getUserId());
        return mlJobRepository.save(job);
    }

    public void deleteJob(String id) throws ElementNotFound {
        var job = getAnyJob(id);
        if (!job.isFinished()) {
            stopRemotely(job);
        }
        log.info("Admin: deleted the scan {} of user {}", id, job.getOwner().getUserId());
        mlJobRepository.delete(job);
    }

    /** Best effort: an unreachable subsystem must not block clearing a stuck scan. */
    private void stopRemotely(MlJob job) {
        if (job.getRemoteJobId() == null) {
            return;
        }
        try {
            proxy.cancel(job.getRemoteJobId());
        } catch (MlSubsystemException e) {
            log.warn("Could not stop job {} at the subsystem: {}", job.getId(), e.getMessage());
        }
    }

    private void markCancelled(MlJob job) {
        job.setStatus(MlJobStatus.CANCELLED);
        job.setFinishedAt(clock.instant());
    }

    /** Withdraw consent: everything this person donated for training is deleted. */
    public int deleteTrainingData(CookpalUser owner) throws MlSubsystemException {
        return proxy.deleteTrainingData(submitterIds.of(owner));
    }

    /** Not @Transactional on purpose: one http call per job would hold a connection for all of them. */
    public void refreshUnfinishedJobs() {
        var unfinished = mlJobRepository
                .findByStatusIn(List.of(MlJobStatus.QUEUED, MlJobStatus.PROCESSING));
        for (var job : unfinished) {
            refresh(job);
        }
    }

    private void refresh(MlJob job) {
        if (hasTimedOut(job)) {
            abandon(job);
            return;
        }
        if (job.getRemoteJobId() == null) {
            // Submission never got an id, so there is nothing to ask about.
            return;
        }

        try {
            apply(job, proxy.fetch(job.getRemoteJobId()));
            availability.reportReachable();
        } catch (MlUnavailableException e) {
            // Briefly unreachable is not the job failing; the timeout is the backstop.
            availability.reportUnreachable();
            log.debug("Could not refresh job {}: {}", job.getId(), e.getMessage());
        } catch (MlSubsystemException e) {
            log.warn("Job {} could not be refreshed and is treated as failed", job.getId(), e);
            fail(job, e);
        }
    }

    private void apply(MlJob job, MlSubsystemProxy.MlJobState state) {
        var code = state.errorCode() == null ? null : MlErrorCodes.of(state.errorCode());
        job.setStatus(state.status());
        job.setResult(state.resultJson());
        job.setErrorCode(code == null ? null : code.name());
        job.setErrorMessage(state.errorMessage());
        job.setErrorRetryable(code != null && code.isRetryable());
        job.setQueuePosition(state.queuePosition());
        if (state.status().isFinished()) {
            job.setFinishedAt(clock.instant());
        }
        mlJobRepository.save(job);
    }

    /** Delete jobs whose results nobody is coming back for. */
    @Transactional
    public int deleteExpiredJobs() {
        var cutoff = clock.instant()
                .minus(Duration.ofHours(configuration.getMl().getJobRetentionHours()));
        return mlJobRepository.deleteByFinishedAtBefore(cutoff);
    }

    private MlJob createQueuedJob(CookpalUser owner, String jobType) {
        return mlJobRepository.save(MlJob.builder()
                .id(UUID.randomUUID().toString())
                .owner(owner)
                .jobType(jobType)
                .status(MlJobStatus.QUEUED)
                .build());
    }

    private void requireRemainingQuota(CookpalUser owner) throws MlSubsystemException {
        var limit = configuration.getMl().getRecipeOcr().getJobsPerUserPerDay();
        if (limit <= 0) {
            return;
        }
        if (usedToday(owner) >= limit) {
            throw new MlSubsystemException(ApiErrorCode.SCAN_DAILY_LIMIT_REACHED,
                    "The daily allowance of " + limit + " scans is used up");
        }
    }

    private long usedToday(CookpalUser owner) {
        return mlJobRepository.countUsageSince(owner, startOfToday());
    }

    /** How many photographs one recipe may span. */
    public int maxPagesPerRecipe() {
        return configuration.getMl().getRecipeOcr().getMaxPages();
    }

    /** What an operator is shown about this instance's use of the subsystem. */
    public record Statistics(long totalJobs, Map<MlJobStatus, Long> jobsByStatus,
            List<MlJob> recentFailures) {
    }

    public Statistics statistics() {
        var counted = mlJobRepository.countGroupedByStatus().stream()
                .collect(Collectors.toMap(MlJobRepository.StatusCount::getStatus,
                        MlJobRepository.StatusCount::getCount));

        // Every state is listed: an absent key would read as "unknown" rather than "none".
        var jobsByStatus = new LinkedHashMap<MlJobStatus, Long>();
        Arrays.stream(MlJobStatus.values())
                .forEach(status -> jobsByStatus.put(status, counted.getOrDefault(status, 0L)));

        return new Statistics(
                jobsByStatus.values().stream().mapToLong(Long::longValue).sum(),
                jobsByStatus,
                mlJobRepository.findTop50ByStatusOrderByCreatedOnDesc(MlJobStatus.FAILED));
    }

    /** What each person has used today. */
    public record QuotaUsage(Long userId, String emailAddress, long used) {
    }

    public List<QuotaUsage> usageToday() {
        return mlJobRepository.findUsagePerUserSince(startOfToday()).stream()
                .map(usage -> new QuotaUsage(usage.getOwner().getUserId(),
                        usage.getOwner().getEmailAddress(), usage.getUsed()))
                .toList();
    }

    /** Gives back today's allowance, returning how many scans stopped counting. */
    @Transactional
    public int resetQuota(CookpalUser owner) {
        var counted = mlJobRepository.findUsageSince(owner, startOfToday());
        counted.forEach(job -> job.setCountsTowardsQuota(false));
        mlJobRepository.saveAll(counted);
        log.info("Admin: reset the scan allowance for user {} ({} job(s))",
                owner.getUserId(), counted.size());
        return counted.size();
    }

    /** The daily allowance, or 0 when there is none. */
    public int dailyQuota() {
        return Math.max(0, configuration.getMl().getRecipeOcr().getJobsPerUserPerDay());
    }

    private Instant startOfToday() {
        return clock.instant().truncatedTo(ChronoUnit.DAYS);
    }

    private boolean hasTimedOut(MlJob job) {
        var deadline = Duration.ofSeconds(configuration.getMl().getJobTimeoutSeconds());
        var startedAt = job.getCreatedOn() == null ? clock.instant() : job.getCreatedOn();
        return Duration.between(startedAt, clock.instant()).compareTo(deadline) > 0;
    }

    private void abandon(MlJob job) {
        log.warn("Job {} was never finished and is being abandoned", job.getId());
        // Failing for a reason of our own rather than one the subsystem gave.
        fail(job, new MlSubsystemException(ApiErrorCode.SCAN_TIMED_OUT,
                "The subsystem did not finish this job in time"));
    }

    /** A scan the subsystem never took on spent nothing, so it must not cost somebody their day. */
    private void failBeforeItWasAccepted(MlJob job, MlSubsystemException failure) {
        job.setCountsTowardsQuota(false);
        fail(job, failure);
    }

    private void fail(MlJob job, MlSubsystemException failure) {
        job.setStatus(MlJobStatus.FAILED);
        job.setErrorCode(failure.getErrorCode().name());
        job.setErrorMessage(failure.getMessage());
        job.setErrorRetryable(failure.getErrorCode().isRetryable());
        job.setFinishedAt(clock.instant());
        mlJobRepository.save(job);
    }

}
