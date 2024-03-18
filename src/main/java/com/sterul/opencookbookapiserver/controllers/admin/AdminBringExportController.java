package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.entities.BringExport;
import com.sterul.opencookbookapiserver.services.BringExportService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/admin/bringexports")
@Tag(name = "Users", description = "Bring exports admin api")
@Slf4j
public class AdminBringExportController {

    private BringExportService bringExportService;


    public AdminBringExportController(BringExportService bringExportService) {
        this.bringExportService = bringExportService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public List<BringExport> getAll() {
        log.info("Admin: Accessing all bring exports");
        return bringExportService.getAllExports();
    }

}
