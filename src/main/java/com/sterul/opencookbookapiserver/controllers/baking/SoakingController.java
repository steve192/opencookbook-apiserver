package com.sterul.opencookbookapiserver.controllers.baking;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.catalogue.CataloguePaths;
import com.sterul.opencookbookapiserver.controllers.catalogue.CatalogueResponses;
import com.sterul.opencookbookapiserver.ratelimiting.RateLimitTier;
import com.sterul.opencookbookapiserver.ratelimiting.RateLimited;
import com.sterul.opencookbookapiserver.services.baking.SoakingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Kitchen tools", description = "What the kitchen tools read beside the catalogue search")
public class SoakingController {

    private final SoakingService soaking;

    public SoakingController(SoakingService soaking) {
        this.soaking = soaking;
    }

    @Operation(summary = "Seeds, flakes and grains for a soaker",
            description = "Catalogue foods the bread tool soaks, with the water each gram soaks up.")
    @RateLimited(RateLimitTier.CHEAP)
    @GetMapping(CataloguePaths.SOAKING)
    public ResponseEntity<List<SoakingFoodResponse>> soaking() {
        var foods = soaking.foods().stream().map(SoakingFoodResponse::of).toList();
        return ResponseEntity.ok().cacheControl(CatalogueResponses.RELEASE_CACHE).body(foods);
    }
}
