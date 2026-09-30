package com.sterul.opencookbookapiserver.controllers.shopping;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.apikeys.ApiKeyAccess;
import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.shopping.requests.ImportLineRequest;
import com.sterul.opencookbookapiserver.controllers.shopping.requests.ImportRequest;
import com.sterul.opencookbookapiserver.controllers.shopping.requests.ShoppingListRequest;
import com.sterul.opencookbookapiserver.controllers.shopping.requests.ShoppingOpRequest;
import com.sterul.opencookbookapiserver.controllers.shopping.requests.ShoppingOpsRequest;
import com.sterul.opencookbookapiserver.controllers.shopping.requests.ShownLineRequest;
import com.sterul.opencookbookapiserver.controllers.shopping.responses.ItemChangesResponse;
import com.sterul.opencookbookapiserver.controllers.shopping.responses.ShoppingListResponse;
import com.sterul.opencookbookapiserver.controllers.support.PlanScopes;
import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.account.ApiScope;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.shopping.ShoppingListService;
import com.sterul.opencookbookapiserver.services.shopping.ShoppingImportService;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingSyncService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Every call about one list names its household, if it has one, the same way plan calls do. */
@RestController
@RequestMapping(ShoppingPaths.BASE + "/lists")
@Tag(name = "Shopping lists", description = "Personal and household shopping lists, synced for offline use")
public class ShoppingListController extends BaseController {

    private final ShoppingListService lists;
    private final ShoppingSyncService sync;
    private final ShoppingImportService imports;
    private final PlanScopes planScopes;

    public ShoppingListController(ShoppingListService lists, ShoppingSyncService sync, ShoppingImportService imports,
            PlanScopes planScopes, SignedInUserService signedInUser) {
        super(signedInUser);
        this.lists = lists;
        this.sync = sync;
        this.imports = imports;
        this.planScopes = planScopes;
    }

    @Operation(summary = "Every list you can use", description = "Your own and your households', default lists "
            + "first. A default list is made the first time it is asked for.")
    @ApiKeyAccess(ApiScope.SHOPPING_READ)
    @GetMapping
    public List<ShoppingListResponse> getLists() {
        return lists.listsIn(planScopes.allVisibleTo(getLoggedInUser())).stream()
                .map(ShoppingListResponse::of).toList();
    }

    @Operation(summary = "Start another list", description = "Yours, or the household's given.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShoppingListResponse create(@RequestParam(required = false) String household,
            @Valid @RequestBody ShoppingListRequest request) {
        return ShoppingListResponse.of(lists.create(scope(household), request.name()));
    }

    @Operation(summary = "Rename a list")
    @PutMapping("/{listId}")
    public ShoppingListResponse rename(@PathVariable Long listId, @RequestParam(required = false) String household,
            @Valid @RequestBody ShoppingListRequest request) {
        return ShoppingListResponse.of(lists.rename(listId, scope(household), request.name()));
    }

    @Operation(summary = "Delete a list", description = "Not a default list; every scope keeps one.")
    @DeleteMapping("/{listId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long listId, @RequestParam(required = false) String household) {
        lists.delete(listId, scope(household));
    }

    @Operation(summary = "What changed since a version",
            description = "The whole list instead, marked full, for a device that never synced or fell too far behind.")
    @ApiKeyAccess(ApiScope.SHOPPING_READ)
    @GetMapping("/{listId}/changes")
    public ItemChangesResponse changes(@PathVariable Long listId, @RequestParam(required = false) String household,
            @RequestParam(defaultValue = "0") long since) {
        return ItemChangesResponse.of(sync.changesSince(listId, scope(household), since));
    }

    @Operation(summary = "Apply what a device changed, possibly offline",
            description = "In the order given; an op id applied before is skipped, so a batch may be retried. "
                    + "Answers what changed since the given version, including other devices' changes.")
    @ApiKeyAccess(ApiScope.SHOPPING_WRITE)
    @PostMapping("/{listId}/ops")
    public ItemChangesResponse applyOps(@PathVariable Long listId, @RequestParam(required = false) String household,
            @RequestParam(defaultValue = "0") long since, @Valid @RequestBody ShoppingOpsRequest request) {
        var ops = request.ops().stream().map(ShoppingOpRequest::toOp).toList();
        return ItemChangesResponse.of(sync.apply(listId, scope(household), ops, since, getLoggedInUser()));
    }

    @Operation(summary = "Add what an import sheet put together",
            description = "Names already on the list ask for more of them. Which offered lines were left out is "
                    + "how staples are learned.")
    @PostMapping("/{listId}/import")
    public ShoppingListResponse importLines(@PathVariable Long listId,
            @RequestParam(required = false) String household, @Valid @RequestBody ImportRequest request) {
        return ShoppingListResponse.of(imports.toList(listId, scope(household),
                request.lines().stream().map(ImportLineRequest::toAdd).toList(),
                request.shown().stream().map(ShownLineRequest::toObservation).toList(), getLoggedInUser()));
    }

    private PlanScope scope(@Nullable String household) {
        return planScopes.of(getLoggedInUser(), household);
    }
}
