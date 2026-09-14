package com.sterul.opencookbookapiserver.controllers;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sterul.opencookbookapiserver.controllers.requests.IngredientRequest;
import com.sterul.opencookbookapiserver.controllers.responses.IngredientResponse;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.services.IngredientService;
import com.sterul.opencookbookapiserver.services.mail.MailLanguages;
import com.sterul.opencookbookapiserver.services.nutrition.linking.CatalogueSearchService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/ingredients")
@Tag(name = "Ingredients", description = "Ingredients used in recipes")
public class IngredientsController extends BaseController {

    @Autowired
    private IngredientService ingredientService;

    @Autowired
    private Optional<CatalogueSearchService> catalogueSearch;

    @Autowired
    private MailLanguages languages;

    @Operation(summary = "The logged in user's ingredients, and names to suggest",
            description = "With nutrition estimation turned on, the catalogue's names in the user's language follow, without ids: "
                    + "a recipe saved with one of them creates the user's own ingredient of that name.")
    @GetMapping("")
    public List<IngredientResponse> all() {
        var user = getLoggedInUser();
        var own = ingredientService.getIngredientsOfUser(user).stream().map(this::entityToResponse).toList();
        var ownNames = own.stream().map(ingredient -> ingredient.getName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        var suggestions = catalogueSearch.stream()
                .flatMap(search -> search.names(languages.forUser(user).getLanguage()).stream())
                .filter(name -> !ownNames.contains(name.toLowerCase(Locale.ROOT)))
                .map(name -> IngredientResponse.builder().name(name).build());
        return Stream.concat(own.stream(), suggestions).toList();
    }

    @Operation(summary = "Get a single ingredient")
    @GetMapping("/{id}")
    public IngredientResponse single(@PathVariable Long id) throws ElementNotFound {
        return entityToResponse(ingredientService.getOwnIngredient(id, getLoggedInUser()));
    }

    @Operation(summary = "Create an ingredient", description = "If an ingredient with the same name exists, the existing ingredient will be returned")
    @PostMapping("")
    public IngredientResponse create(@RequestBody IngredientRequest newIngredient) {
        return entityToResponse(
                ingredientService.createOrGetIngredient(requestToEntity(newIngredient), getLoggedInUser()));
    }

    private Ingredient requestToEntity(IngredientRequest ingredientRequest) {
        return Ingredient.builder()
                .name(ingredientRequest.getName())
                .additionalInfo(ingredientRequest.getAdditionalInfo())
                .build();
    }

    private IngredientResponse entityToResponse(Ingredient ingredient) {
        return IngredientResponse.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .additionalInfo(ingredient.getAdditionalInfo())
                .build();
    }

}
