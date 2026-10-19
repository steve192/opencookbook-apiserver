package com.sterul.opencookbookapiserver.controllers.seasonal;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.catalogue.CataloguePaths;
import com.sterul.opencookbookapiserver.controllers.catalogue.CatalogueResponses;
import com.sterul.opencookbookapiserver.ratelimiting.RateLimitTier;
import com.sterul.opencookbookapiserver.ratelimiting.RateLimited;
import com.sterul.opencookbookapiserver.services.seasonal.SeasonalCalendarService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;

@RestController
@Tag(name = "Kitchen tools", description = "What the kitchen tools read beside the catalogue search")
public class SeasonalCalendarController {

    private final SeasonalCalendarService seasonalCalendar;

    public SeasonalCalendarController(SeasonalCalendarService seasonalCalendar) {
        this.seasonalCalendar = seasonalCalendar;
    }

    @Operation(summary = "Fruit and vegetables with a season in a region",
            description = "Per month how each food comes to market and how it keeps at home. Only DE so far.")
    @RateLimited(RateLimitTier.CHEAP)
    @GetMapping(CataloguePaths.SEASONS)
    public ResponseEntity<List<SeasonalFoodResponse>> seasons(
            @RequestParam(defaultValue = SeasonalCalendarService.GERMANY) @Pattern(regexp = "[A-Z]{2}") String region,
            @RequestParam @Pattern(regexp = CatalogueResponses.LANGUAGE) String lang) {
        var foods = seasonalCalendar.produce(region).stream()
                .map(produce -> SeasonalFoodResponse.of(produce, region, lang))
                .sorted(Comparator.comparing(SeasonalFoodResponse::displayName,
                        Collator.getInstance(Locale.forLanguageTag(lang))))
                .toList();
        return ResponseEntity.ok().cacheControl(CatalogueResponses.RELEASE_CACHE).body(foods);
    }
}
