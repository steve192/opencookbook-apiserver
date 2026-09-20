package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.nutrition.ConditionalOnNutritionEnabled;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminUnmatchedNameResponse;
import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminUserCorrectionResponse;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.nutrition.reports.CoverageReport;
import com.sterul.opencookbookapiserver.services.nutrition.reports.IngredientNameReport;
import com.sterul.opencookbookapiserver.services.nutrition.reports.UserCorrectionReport;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/admin/nutrition")
@Tag(name = "Nutrition reports", description = "Unmatched names, user corrections, the production names export and coverage")
@ConditionalOnNutritionEnabled
@Slf4j
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminNutritionReportController {

    private static final MediaType TAB_SEPARATED_VALUES = MediaType.parseMediaType("text/tab-separated-values;charset=UTF-8");

    private final IngredientNameReport nameReport;
    private final CoverageReport coverageReport;
    private final UserCorrectionReport correctionReport;

    public AdminNutritionReportController(IngredientNameReport nameReport, CoverageReport coverageReport,
            UserCorrectionReport correctionReport) {
        this.nameReport = nameReport;
        this.coverageReport = coverageReport;
        this.correctionReport = correctionReport;
    }

    @Operation(summary = "Names the catalogue does not answer silently", description = "The names most users wrote first.")
    @GetMapping("/unmatched-names")
    public List<AdminUnmatchedNameResponse> getUnmatchedNames(@RequestParam(defaultValue = "200") @Min(1) @Max(1000) int limit)
            throws ApiException {
        return nameReport.unmatchedNames(limit).stream().map(AdminUnmatchedNameResponse::of).toList();
    }

    @Operation(summary = "Where users linked a name otherwise than the matcher would",
            description = "The corrections the most users made first; each is a candidate name for a food, or a name that is no food.")
    @GetMapping("/user-corrections")
    public List<AdminUserCorrectionResponse> getUserCorrections(@RequestParam(defaultValue = "200") @Min(1) @Max(1000) int limit)
            throws ApiException {
        return correctionReport.corrections(limit).stream().map(AdminUserCorrectionResponse::of).toList();
    }

    @Operation(summary = "Every ingredient name used, as a draft of the production gold set",
            description = "Tab-separated, for nutrition-data/local/names-production.tsv. Contains what users wrote: never commit it.")
    @GetMapping("/names-export")
    public ResponseEntity<String> exportNames() {
        log.info("Admin: Exporting the ingredient names of all users");
        return ResponseEntity.ok()
                .contentType(TAB_SEPARATED_VALUES)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"names-production.tsv\"")
                .body(nameReport.productionNames());
    }

    @Operation(summary = "The nutrition calculator run over every recipe")
    @GetMapping("/coverage")
    public CoverageReport.Coverage getCoverage() {
        return coverageReport.coverage();
    }
}
