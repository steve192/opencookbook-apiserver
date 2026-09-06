package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.ml.ConditionalOnMlConfigured;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminMlJobResponse;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminMlQuotaResponse;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminMlStatisticsResponse;
import com.sterul.opencookbookapiserver.entities.ml.MlJobStatus;
import com.sterul.opencookbookapiserver.services.UserService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.ml.MlAvailabilityService;
import com.sterul.opencookbookapiserver.services.ml.MlJobService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/** What an operator can see and do about this instance's use of the subsystem. */
@RestController
@ConditionalOnMlConfigured
@RequestMapping("/api/v1/admin/ml")
@Tag(name = "Machine learning", description = "Admin view of this instance's ml usage")
@Slf4j
public class AdminMlController {

    private final MlAvailabilityService availability;
    private final MlJobService mlJobService;
    private final UserService userService;

    public AdminMlController(MlAvailabilityService availability, MlJobService mlJobService,
            UserService userService) {
        this.availability = availability;
        this.mlJobService = mlJobService;
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminMlStatisticsResponse getStatistics() {
        log.info("Admin: Accessing machine learning statistics");

        var statistics = mlJobService.statistics();
        return AdminMlStatisticsResponse.builder()
                .available(availability.isAvailable())
                .totalJobs(statistics.totalJobs())
                .jobsByStatus(statistics.jobsByStatus())
                .recentFailures(statistics.recentFailures().stream()
                        .map(job -> AdminMlStatisticsResponse.Failure.builder()
                                .id(job.getId())
                                .jobType(job.getJobType())
                                .code(job.getErrorCode())
                                .message(job.getErrorMessage())
                                .failedAt(job.getFinishedAt())
                                .build())
                        .toList())
                .build();
    }

    @Operation(summary = "Every scan on this instance, newest first",
            description = "Optionally narrowed to one person or one state.")
    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<AdminMlJobResponse> getJobs(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) MlJobStatus status) {
        log.info("Admin: Accessing the scan jobs");
        return mlJobService.jobs(userId, status).stream()
                .map(AdminMlJobResponse::fromEntity)
                .toList();
    }

    @Operation(summary = "Let somebody try a scan again",
            description = "A scan still running is stopped, and it stops counting against its "
                    + "owner's daily allowance.")
    @PostMapping("/jobs/{id}/reset")
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminMlJobResponse resetJob(@PathVariable String id) throws ElementNotFound {
        return AdminMlJobResponse.fromEntity(mlJobService.resetJob(id));
    }

    @Operation(summary = "Delete a scan", description = "Stops it first if it is still running.")
    @DeleteMapping("/jobs/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteJob(@PathVariable String id) throws ElementNotFound {
        mlJobService.deleteJob(id);
    }

    @Operation(summary = "How much of today's scan allowance each person has used",
            description = "Only people who have run a scan today are listed; everyone else has "
                    + "used nothing. A daily limit of 0 means there is no limit.")
    @GetMapping("/quota")
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminMlQuotaResponse getQuotaUsage() {
        var limit = mlJobService.dailyQuota();
        return AdminMlQuotaResponse.builder()
                .dailyLimit(limit)
                .users(mlJobService.usageToday().stream()
                        .map(usage -> AdminMlQuotaResponse.Usage.builder()
                                .userId(usage.userId())
                                .emailAddress(usage.emailAddress())
                                .used(usage.used())
                                .exhausted(limit > 0 && usage.used() >= limit)
                                .build())
                        .sorted(Comparator.comparingLong(AdminMlQuotaResponse.Usage::getUsed)
                                .reversed())
                        .toList())
                .build();
    }

    @Operation(summary = "Give somebody their scan allowance back for the rest of today",
            description = "The scans themselves are kept and stop counting, so the record of "
                    + "what was run survives the exception being granted.")
    @PostMapping("/quota/{userId}/reset")
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminMlQuotaResponse resetQuota(@PathVariable Long userId) throws ElementNotFound {
        mlJobService.resetQuota(userService.getUserById(userId));
        return getQuotaUsage();
    }
}
