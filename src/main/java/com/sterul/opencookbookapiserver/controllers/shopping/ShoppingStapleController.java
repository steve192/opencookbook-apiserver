package com.sterul.opencookbookapiserver.controllers.shopping;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.shopping.responses.StapleResponse;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.shopping.StapleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(ShoppingPaths.BASE + "/staples")
@Tag(name = "Shopping import")
public class ShoppingStapleController extends BaseController {

    private final StapleService staples;

    public ShoppingStapleController(StapleService staples, SignedInUserService signedInUser) {
        super(signedInUser);
        this.staples = staples;
    }

    @Operation(summary = "What you keep at home", description = "Lines you left out of three imports in a row; "
            + "an import sheet offers them unticked.")
    @GetMapping
    public List<StapleResponse> getStaples() {
        return staples.staplesOf(getLoggedInUser()).stream().map(StapleResponse::of).toList();
    }

    @Operation(summary = "Stop treating a line as a staple")
    @DeleteMapping("/{stapleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forget(@PathVariable Long stapleId) {
        staples.forget(stapleId, getLoggedInUser());
    }
}
