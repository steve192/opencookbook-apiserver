package com.sterul.opencookbookapiserver.controllers.admin;

import java.time.Clock;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminBringExportResponse;
import com.sterul.opencookbookapiserver.services.BringExportService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/admin/bringexports")
@Tag(name = "Bring exports", description = "Bring exports admin api")
@Slf4j
public class AdminBringExportController {

    private final BringExportService bringExportService;
    private final Clock clock;

    public AdminBringExportController(BringExportService bringExportService, Clock clock) {
        this.bringExportService = bringExportService;
        this.clock = clock;
    }

    @Operation(summary = "Every shopping list handed to Bring, with who exported it")
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<AdminBringExportResponse> getAll() {
        log.info("Admin: Accessing all bring exports");
        var now = clock.instant();
        return bringExportService.getAllExports().stream()
                .map(export -> AdminBringExportResponse.fromEntity(export, now))
                .toList();
    }

    @Operation(summary = "Delete an export before it lapses on its own")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExport(@PathVariable String id) throws ElementNotFound {
        log.info("Admin: Deleting bring export {}", id);
        bringExportService.deleteExport(id);
    }
}
