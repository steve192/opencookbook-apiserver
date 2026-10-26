package com.sterul.opencookbookapiserver.controllers.shopping;

import java.util.stream.Stream;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.configurations.apikeys.ApiKeyAccess;
import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.controllers.shopping.responses.ShoppingTileResponse;
import com.sterul.opencookbookapiserver.controllers.shopping.responses.ShoppingVocabularyResponse;
import com.sterul.opencookbookapiserver.entities.account.ApiScope;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.catalogue.NonFoodItems;
import com.sterul.opencookbookapiserver.services.catalogue.UnitLexicon;
import com.sterul.opencookbookapiserver.services.shopping.tiles.ShoppingTileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Shopping lists")
public class ShoppingVocabularyController extends BaseController {

    private final ShoppingTileService tiles;
    private final NonFoodItems nonFood;
    private final UnitLexicon unitLexicon;

    public ShoppingVocabularyController(ShoppingTileService tiles, NonFoodItems nonFood, UnitLexicon unitLexicon,
            SignedInUserService signedInUser) {
        super(signedInUser);
        this.tiles = tiles;
        this.nonFood = nonFood;
        this.unitLexicon = unitLexicon;
    }

    @Operation(summary = "What the app needs to add to a list offline",
            description = "Foods of the catalogue and things that are no food to tap, and the catalogue's unit "
                    + "words, which tell \"2 kg Kartoffeln\" apart from \"2 Pizzateige\".")
    @ApiKeyAccess(ApiScope.SHOPPING_READ)
    @GetMapping(ShoppingPaths.VOCABULARY)
    public ShoppingVocabularyResponse getVocabulary() {
        var offered = Stream.concat(
                tiles.tiles().stream().map(ShoppingTileResponse::of),
                nonFood.items().stream().map(ShoppingTileResponse::of)).toList();
        return new ShoppingVocabularyResponse(offered, unitLexicon.words().stream().sorted().toList());
    }
}
