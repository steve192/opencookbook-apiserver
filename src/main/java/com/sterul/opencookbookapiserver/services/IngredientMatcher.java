package com.sterul.opencookbookapiserver.services;

import static com.intuit.fuzzymatcher.domain.ElementType.NAME;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.intuit.fuzzymatcher.component.MatchService;
import com.intuit.fuzzymatcher.domain.Document;
import com.intuit.fuzzymatcher.domain.Element;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

@Component
public class IngredientMatcher {
    public static final String NEW_INDREGIENT_KEY = "newIngredient";

    public Ingredient findIngredientbySimilarName(List<Ingredient> possibleIngredients, String name)
            throws ElementNotFound {
        if (possibleIngredients == null || possibleIngredients.isEmpty()) {
            throw new ElementNotFound();
        }
        var documentList = possibleIngredients.stream()
                .map(ingredient -> new Document.Builder(ingredient.getId().toString())
                        .addElement(new Element.Builder<String>()
                                .setValue(ingredient.getName())
                                .setType(NAME)
                                .createElement())
                        .createDocument())
                .collect(Collectors.toList());

        documentList.add(new Document.Builder(NEW_INDREGIENT_KEY)
                .addElement(new Element.Builder<String>()
                        .setValue(name)
                        .setType(NAME)
                        .createElement())
                .createDocument());

        MatchService matchService = new MatchService();
        var matches = matchService.applyMatchByDocId(documentList);
        if (matches.size() == 0) {
            throw new ElementNotFound();
        }
        var matchesForNamedIngredient = matches.get(NEW_INDREGIENT_KEY);

        if (matchesForNamedIngredient == null) {
            throw new ElementNotFound();
        }

        var bestMatchedIngredientId = Float.parseFloat(matchesForNamedIngredient.get(0).getMatchedWith().getKey());

        var bestMatchedIngredient = possibleIngredients.stream()
                .filter(ingredient -> ingredient.getId() == bestMatchedIngredientId)
                .findFirst();

        if (bestMatchedIngredient.isEmpty()) {
            throw new ElementNotFound();
        }
        return bestMatchedIngredient.get();
    }

}
