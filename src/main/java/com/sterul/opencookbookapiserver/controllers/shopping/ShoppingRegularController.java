package com.sterul.opencookbookapiserver.controllers.shopping;

import java.util.List;

import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.shopping.requests.ShoppingRegularRequest;
import com.sterul.opencookbookapiserver.controllers.shopping.responses.ShoppingRegularResponse;
import com.sterul.opencookbookapiserver.controllers.support.PlanScopes;
import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.shopping.history.ShoppingRegulars;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** What a list buys at a rhythm, and when to suggest it. Like every call about a list, it names the list's household. */
@RestController
@RequestMapping(ShoppingPaths.BASE + "/lists/{listId}/regulars")
@Tag(name = "Shopping lists")
public class ShoppingRegularController extends BaseController {

    private final ShoppingRegulars regulars;
    private final PlanScopes planScopes;

    public ShoppingRegularController(ShoppingRegulars regulars, PlanScopes planScopes,
            SignedInUserService signedInUser) {
        super(signedInUser);
        this.regulars = regulars;
        this.planScopes = planScopes;
    }

    @Operation(summary = "What the list buys regularly",
            description = "Names bought at a steady rhythm, with the rhythm and the windows in which a device shows "
                    + "each as a suggestion, and names somebody chose not to have suggested.")
    @GetMapping
    public List<ShoppingRegularResponse> regulars(@PathVariable Long listId,
            @RequestParam(required = false) String household) {
        return regulars.regularsOf(listId, scope(household)).stream().map(ShoppingRegularResponse::of).toList();
    }

    @Operation(summary = "Not now: suggest a regular item again later", description = "Half a rhythm later.")
    @PostMapping("/{regularId}/not-now")
    public ShoppingRegularResponse notNow(@PathVariable Long listId, @PathVariable Long regularId,
            @RequestParam(required = false) String household) {
        return ShoppingRegularResponse.of(regulars.notNow(listId, scope(household), regularId));
    }

    @Operation(summary = "Stop or resume suggesting a regular item")
    @PutMapping("/{regularId}")
    public ShoppingRegularResponse mute(@PathVariable Long listId, @PathVariable Long regularId,
            @RequestParam(required = false) String household, @Valid @RequestBody ShoppingRegularRequest request) {
        return ShoppingRegularResponse.of(regulars.mute(listId, scope(household), regularId, request.muted()));
    }

    private PlanScope scope(@Nullable String household) {
        return planScopes.of(getLoggedInUser(), household);
    }
}
