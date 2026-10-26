package com.sterul.opencookbookapiserver.controllers.shopping;

import java.util.List;

import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.shopping.responses.ShoppingEntryResponse;
import com.sterul.opencookbookapiserver.controllers.shopping.responses.ShoppingHistoryResponse;
import com.sterul.opencookbookapiserver.controllers.shopping.responses.ShoppingListEventResponse;
import com.sterul.opencookbookapiserver.controllers.support.PlanScopes;
import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.shopping.history.ShoppingHistory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.PositiveOrZero;

/** What people changed on a list. Like every call about a list, it names the list's household. */
@RestController
@RequestMapping(ShoppingPaths.BASE + "/lists/{listId}")
@Tag(name = "Shopping lists")
public class ShoppingHistoryController extends BaseController {

    private final ShoppingHistory history;
    private final PlanScopes planScopes;

    public ShoppingHistoryController(ShoppingHistory history, PlanScopes planScopes,
            SignedInUserService signedInUser) {
        super(signedInUser);
        this.history = history;
        this.planScopes = planScopes;
    }

    @Operation(summary = "Who changed what on the list",
            description = "People's changes to its items, newest first, a page at a time: added, ticked off, put "
                    + "back, removed and edited. Kept for a year; an item taken off within two minutes left none.")
    @GetMapping("/history")
    public ShoppingHistoryResponse history(@PathVariable Long listId, @RequestParam(required = false) String household,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page) {
        var viewer = getLoggedInUser();
        var events = history.historyOf(listId, scope(household), page);
        return new ShoppingHistoryResponse(
                events.stream().map(event -> ShoppingListEventResponse.of(event, viewer)).toList(), !events.hasNext());
    }

    @Operation(summary = "Names the list held lately",
            description = "Each name once, as last put on the list, newest first; offered again while typing. "
                    + "Names not put on the list for half a year, and names taken off within two minutes, are left out.")
    @GetMapping("/entries")
    public List<ShoppingEntryResponse> entries(@PathVariable Long listId,
            @RequestParam(required = false) String household) {
        return history.entriesOf(listId, scope(household)).stream().map(ShoppingEntryResponse::of).toList();
    }

    private PlanScope scope(@Nullable String household) {
        return planScopes.of(getLoggedInUser(), household);
    }
}
