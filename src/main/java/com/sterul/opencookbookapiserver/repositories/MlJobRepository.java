package com.sterul.opencookbookapiserver.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.ml.MlJob;
import com.sterul.opencookbookapiserver.entities.ml.MlJobStatus;

public interface MlJobRepository extends JpaRepository<MlJob, String> {

    Optional<MlJob> findByIdAndOwner(String id, CookpalUser owner);

    List<MlJob> findByStatusIn(List<MlJobStatus> statuses);

    // These three ask the same question of different scopes, so they spell the predicate the
    // same way. Written out rather than derived from the method name: the derived spelling of
    // "counts towards quota and created no earlier than" is longer than the query itself.

    /** How many of somebody's scans since the given moment still count against their day. */
    @Query("select count(job) from MlJob job where job.owner = :owner "
            + "and job.createdOn >= :since and job.countsTowardsQuota = true")
    long countUsageSince(@Param("owner") CookpalUser owner, @Param("since") Instant since);

    /** Those same scans, for when they are to be stopped counting. */
    @Query("select job from MlJob job where job.owner = :owner "
            + "and job.createdOn >= :since and job.countsTowardsQuota = true")
    List<MlJob> findUsageSince(@Param("owner") CookpalUser owner, @Param("since") Instant since);

    /** Everyone who has run a scan since the given moment, and how many they ran. */
    @Query("select job.owner as owner, count(job) as used from MlJob job "
            + "where job.createdOn >= :since and job.countsTowardsQuota = true "
            + "group by job.owner")
    List<QuotaUsage> findUsagePerUserSince(@Param("since") Instant since);

    interface QuotaUsage {
        CookpalUser getOwner();

        long getUsed();
    }

    int deleteByFinishedAtBefore(Instant cutoff);

    /** How many jobs are in each state, in one pass rather than a count per state. */
    @Query("select job.status as status, count(job) as count from MlJob job group by job.status")
    List<StatusCount> countGroupedByStatus();

    interface StatusCount {
        MlJobStatus getStatus();

        long getCount();
    }

    List<MlJob> findTop50ByStatusOrderByCreatedOnDesc(MlJobStatus status);
}
