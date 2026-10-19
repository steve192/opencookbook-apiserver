package com.sterul.opencookbookapiserver.controllers.catalogue;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.BaseController;
import com.sterul.opencookbookapiserver.services.SignedInUserService;
import com.sterul.opencookbookapiserver.services.mail.MailLanguages;
import com.sterul.opencookbookapiserver.services.catalogue.linking.IngredientLinkService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@RestController
@Tag(name = "Nutrition", description = "Estimated nutrients of recipes, and the links behind them")
public class IngredientLinkController extends BaseController {

    private final IngredientLinkService linkService;
    private final MailLanguages languages;

    public IngredientLinkController(IngredientLinkService linkService, MailLanguages languages,
            SignedInUserService signedInUser) {
        super(signedInUser);
        this.linkService = linkService;
        this.languages = languages;
    }

    @Operation(summary = "Say what one of your ingredients is",
            description = "Applies to every recipe using the ingredient. Automatic matching never overrides it.")
    @PutMapping("/api/v1/ingredients/{id}/link")
    public IngredientLinkResponse link(@PathVariable Long id, @Valid @RequestBody IngredientLinkRequest request) {
        var owner = getLoggedInUser();
        var ingredient = request.isExcluded()
                ? linkService.exclude(id, owner)
                : linkService.link(id, owner, request.catalogueFoodId());
        return IngredientLinkResponse.of(ingredient, language());
    }

    /** @param unit as the recipe writes it; empty for pieces */
    public record OwnPortionRequest(String unit, @NotNull @Positive @Max(10000) Float grams) {
    }

    @Operation(summary = "Say what a piece of one of your ingredients weighs",
            description = "For a unit that counts pieces, e.g. {\"unit\": \"Stück\", \"grams\": 200}. Applies to every recipe using "
                    + "the ingredient with that unit, and counts before the catalogue's weights.")
    @PutMapping("/api/v1/ingredients/{id}/portion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setOwnPortion(@PathVariable Long id, @Valid @RequestBody OwnPortionRequest request) {
        linkService.setOwnPortion(id, getLoggedInUser(), request.unit(), request.grams());
    }

    @Operation(summary = "Take back what you said a piece of an ingredient weighs")
    @DeleteMapping("/api/v1/ingredients/{id}/portion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeOwnPortion(@PathVariable Long id, @RequestParam(defaultValue = "") String unit) {
        linkService.removeOwnPortion(id, getLoggedInUser(), unit);
    }

    private String language() {
        return languages.forUser(getLoggedInUser()).getLanguage();
    }
}
