package com.sterul.opencookbookapiserver.controllers.catalogue;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.ratelimiting.RateLimitTier;
import com.sterul.opencookbookapiserver.ratelimiting.RateLimited;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.catalogue.linking.CatalogueSearchService;
import com.sterul.opencookbookapiserver.services.mail.MailLanguages;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@RestController
@Tag(name = "Catalogue", description = "The food catalogue, as ingredient linking and the kitchen tools search it")
public class CatalogueController extends BaseController {

    private static final String DEFAULT_RESULTS = "20";

    private final CatalogueSearchService searchService;
    private final MailLanguages languages;

    public CatalogueController(CatalogueSearchService searchService, MailLanguages languages,
            SignedInUserService signedInUser) {
        super(signedInUser);
        this.searchService = searchService;
        this.languages = languages;
    }

    @Operation(summary = "Search the catalogue",
            description = "Most likely foods first. Names in lang, else the user's or the request's language.")
    @RateLimited(RateLimitTier.CHEAP)
    @GetMapping(CataloguePaths.SEARCH)
    public List<CatalogueFoodResponse> search(@RequestParam @NotBlank String q,
            @RequestParam(required = false) @Pattern(regexp = CatalogueResponses.LANGUAGE) String lang,
            @RequestParam(defaultValue = DEFAULT_RESULTS) @Min(1) @Max(50) int limit) {
        var language = lang != null ? lang
                : findLoggedInUser().map(languages::forUser).orElseGet(languages::forCurrentRequest).getLanguage();
        return searchService.search(q, language, limit).stream()
                .map(found -> CatalogueFoodResponse.of(found.food(), language, found.confidence()))
                .toList();
    }
}
