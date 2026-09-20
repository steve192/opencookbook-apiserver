package com.sterul.opencookbookapiserver.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.requests.PlanDraftRequest;
import com.sterul.opencookbookapiserver.controllers.requests.PlanningProfileRequest;
import com.sterul.opencookbookapiserver.controllers.responses.PlanDraftResponse;
import com.sterul.opencookbookapiserver.controllers.responses.PlanningProfileResponse;
import com.sterul.opencookbookapiserver.controllers.support.RecipeResponses;
import com.sterul.opencookbookapiserver.entities.planning.PlanDraft;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.planning.PlanDraftService;
import com.sterul.opencookbookapiserver.services.planning.PlanningProfileService;
import com.sterul.opencookbookapiserver.services.planning.RerollReason;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/planning")
@Tag(name = "Weekplan generation", description = "Planning profiles, and proposed weeks generated from them")
public class PlanningController extends BaseController {

    private final PlanningProfileService profileService;
    private final PlanDraftService draftService;
    private final RecipeResponses recipeResponses;

    public PlanningController(PlanningProfileService profileService, PlanDraftService draftService,
            RecipeResponses recipeResponses) {
        this.profileService = profileService;
        this.draftService = draftService;
        this.recipeResponses = recipeResponses;
    }

    public record LockRequest(@NotNull Boolean locked) {
    }

    /** @param reason why the recipe was passed over; null for simply something else */
    public record RerollRequest(RerollReason reason) {
    }

    @Operation(summary = "The caller's planning profiles")
    @GetMapping("/profiles")
    public List<PlanningProfileResponse> getProfiles() {
        return profileService.getProfiles(getLoggedInUser()).stream().map(PlanningProfileResponse::fromEntity).toList();
    }

    @Operation(summary = "One planning profile")
    @GetMapping("/profiles/{id}")
    public PlanningProfileResponse getProfile(@PathVariable Long id) throws ElementNotFound {
        return PlanningProfileResponse.fromEntity(profileService.getProfile(id, getLoggedInUser()));
    }

    @Operation(summary = "Save the answers of the weekplan wizard", description = "The first profile becomes the default.")
    @PostMapping("/profiles")
    public PlanningProfileResponse createProfile(@Valid @RequestBody PlanningProfileRequest request) {
        return PlanningProfileResponse.fromEntity(profileService.create(getLoggedInUser(), request.toProfile()));
    }

    @Operation(summary = "Change a planning profile")
    @PutMapping("/profiles/{id}")
    public PlanningProfileResponse updateProfile(@PathVariable Long id, @Valid @RequestBody PlanningProfileRequest request)
            throws ElementNotFound {
        return PlanningProfileResponse.fromEntity(profileService.update(id, getLoggedInUser(), request.toProfile()));
    }

    @Operation(summary = "Delete a planning profile", description = "Drafts made from it can no longer be adjusted.")
    @DeleteMapping("/profiles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProfile(@PathVariable Long id) throws ElementNotFound {
        profileService.delete(id, getLoggedInUser());
    }

    @Operation(summary = "Propose a week",
            description = "Generates a draft from a profile. Nothing reaches the weekplan until the draft is accepted.")
    @PostMapping("/drafts")
    public PlanDraftResponse generate(@Valid @RequestBody PlanDraftRequest request) throws ElementNotFound {
        return draft(draftService.generate(getLoggedInUser(), request.profileId(),
                request.startDate(), request.daysOrWeek(), request.skippedOrNone()));
    }

    @Operation(summary = "One proposed week")
    @GetMapping("/drafts/{id}")
    public PlanDraftResponse getDraft(@PathVariable Long id) throws ElementNotFound {
        return draft(draftService.getDraft(id, getLoggedInUser()));
    }

    @Operation(summary = "Something else for one meal",
            description = "Never the recipe the meal had. A reason steers the replacement away from what put the "
                    + "cook off: a different main food, less work, or fewer of the same ingredients.")
    @PostMapping("/drafts/{id}/slots/{slotId}/reroll")
    public PlanDraftResponse reroll(@PathVariable Long id, @PathVariable Long slotId,
            @RequestBody(required = false) RerollRequest request) throws ElementNotFound {
        var reason = request == null ? null : request.reason();
        return draft(draftService.reroll(getLoggedInUser(), id, slotId, reason));
    }

    @Operation(summary = "Keep or release a meal", description = "A locked meal stays as it is when the week is drawn again.")
    @PostMapping("/drafts/{id}/slots/{slotId}/lock")
    public PlanDraftResponse lock(@PathVariable Long id, @PathVariable Long slotId, @Valid @RequestBody LockRequest request)
            throws ElementNotFound {
        return draft(draftService.setLocked(getLoggedInUser(), id, slotId, request.locked()));
    }

    @Operation(summary = "Turn a meal into a gap, or a gap into a meal",
            description = "A gap is left to the cook - for the meals nobody keeps in a cookbook.")
    @PostMapping("/drafts/{id}/slots/{slotId}/toggle-gap")
    public PlanDraftResponse toggleGap(@PathVariable Long id, @PathVariable Long slotId) throws ElementNotFound {
        return draft(draftService.toggleGap(getLoggedInUser(), id, slotId));
    }

    @Operation(summary = "Draw every meal that is not locked again")
    @PostMapping("/drafts/{id}/reroll")
    public PlanDraftResponse rerollAll(@PathVariable Long id) throws ElementNotFound {
        return draft(draftService.rerollAll(getLoggedInUser(), id));
    }

    @Operation(summary = "Write the week into the weekplan",
            description = "Adds the meals to each day in the order of the day; meals planned by hand stay. Gaps are "
                    + "not written.")
    @PostMapping("/drafts/{id}/accept")
    public PlanDraftResponse accept(@PathVariable Long id) throws ElementNotFound {
        return draft(draftService.accept(getLoggedInUser(), id));
    }

    @Operation(summary = "Throw a proposed week away")
    @DeleteMapping("/drafts/{id}")
    public PlanDraftResponse discard(@PathVariable Long id) throws ElementNotFound {
        return draft(draftService.discard(getLoggedInUser(), id));
    }

    private PlanDraftResponse draft(PlanDraft draft) {
        return PlanDraftResponse.fromEntity(draft, recipeResponses::of);
    }
}
