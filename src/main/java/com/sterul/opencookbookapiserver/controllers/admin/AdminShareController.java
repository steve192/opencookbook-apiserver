package com.sterul.opencookbookapiserver.controllers.admin;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminShareResponse;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminShareStatisticsResponse;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.sharing.ShareLinkFactory;
import com.sterul.opencookbookapiserver.services.sharing.ShareService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;

/**
 * Moderating what this instance publishes.
 */
@RestController
@RequestMapping("/api/v1/admin/shares")
@Tag(name = "Recipe shares", description = "Moderating what this instance publishes")
@Slf4j
public class AdminShareController {

    /** How far ahead the overview counts a share as expiring soon. */
    private static final Duration EXPIRING_SOON = Duration.ofDays(7);

    private final ShareService shareService;
    private final ShareLinkFactory shareLinkFactory;
    private final Clock clock;

    public AdminShareController(ShareService shareService, ShareLinkFactory shareLinkFactory, Clock clock) {
        this.shareService = shareService;
        this.shareLinkFactory = shareLinkFactory;
        this.clock = clock;
    }

    @Operation(summary = "Every share on this instance")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<AdminShareResponse> getAll() {
        log.info("Admin: Accessing all shares");
        var now = clock.instant();
        return shareService.getAllShares().stream()
                .map(share -> AdminShareResponse.fromEntity(share, shareLinkFactory, now))
                .toList();
    }

    @Operation(summary = "Sharing totals")
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('ADMIN')")
    public AdminShareStatisticsResponse getStatistics() {
        var statistics = shareService.getStatistics(EXPIRING_SOON);
        return new AdminShareStatisticsResponse(
                statistics.totalShares(),
                statistics.totalAccesses(),
                statistics.expiringSoon());
    }

    @Operation(summary = "Take a share down", description = "Revokes any share regardless of who owns it. The link stops working immediately.")
    @DeleteMapping("/{shareId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeShare(@Valid @NotBlank @PathVariable String shareId) throws ElementNotFound {
        log.info("Admin: Revoking share {}", shareId);
        shareService.revokeAsAdministrator(shareId);
    }

}
