package com.sterul.opencookbookapiserver.controllers.admin;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.admin.responses.AdminHouseholdResponse;
import com.sterul.opencookbookapiserver.services.households.HouseholdMembershipService;
import com.sterul.opencookbookapiserver.services.households.HouseholdService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;

/** Not conditional on households being enabled, so what exists can still be seen and dissolved. */
@RestController
@RequestMapping("/api/v1/admin/households")
@Tag(name = "Households", description = "Moderating the households on this instance")
@Slf4j
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminHouseholdController {

    private final HouseholdService householdService;
    private final HouseholdMembershipService memberships;

    public AdminHouseholdController(HouseholdService householdService, HouseholdMembershipService memberships) {
        this.householdService = householdService;
        this.memberships = memberships;
    }

    @Operation(summary = "Every household on this instance")
    @GetMapping
    public List<AdminHouseholdResponse> getAll() {
        log.info("Admin: Accessing all households");
        return householdService.getAllForAdministration().stream()
                .map(household -> AdminHouseholdResponse.fromEntity(household,
                        memberships.membersOf(household.getId())))
                .toList();
    }

    @Operation(summary = "Dissolve a household",
            description = "Removes every membership and the household itself. No recipe is deleted: "
                    + "a household owns none, so every member keeps their own cookbook.")
    @DeleteMapping("/{householdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dissolve(@Valid @NotBlank @PathVariable String householdId) {
        log.info("Admin: Dissolving household {}", householdId);
        householdService.dissolveAsAdministrator(householdId);
    }
}
