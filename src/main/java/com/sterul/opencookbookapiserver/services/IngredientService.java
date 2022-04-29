package com.sterul.opencookbookapiserver.services;

import com.intuit.fuzzymatcher.component.MatchService;
import com.intuit.fuzzymatcher.domain.Document;
import com.intuit.fuzzymatcher.domain.Element;
import com.sterul.opencookbookapiserver.entities.Ingredient;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.IngredientRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intuit.fuzzymatcher.domain.ElementType.NAME;

@Service
@Transactional
public class IngredientService {

    public static final String NEW_INDREGIENT_KEY = "newIngredient";
    @Autowired
    private IngredientRepository ingredientRepository;

    public Ingredient findUserIngredientBySimilarName(String name, User user) throws ElementNotFound {
        var ingredients = getUserPermittedIngredients(user);

        var documentList = ingredients.stream().map(ingredient ->
                new Document.Builder(ingredient.getId().toString())
                        .addElement(new Element.Builder<String>()
                                .setValue(ingredient.getName())
                                .setType(NAME)
                                .createElement())
                        .createDocument()
        ).collect(Collectors.toList());

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

        var bestMatchedIngredient = ingredients.stream()
                .filter(ingredient -> ingredient.getId() == bestMatchedIngredientId)
                .findFirst();

        if (bestMatchedIngredient.isEmpty()) {
            throw new ElementNotFound();
        }
        return bestMatchedIngredient.get();
    }

    public Ingredient createOrGetIngredient(Ingredient ingredient, User user) {


        var publicIngredient = ingredientRepository.findByNameAndIsPublicIngredient(
                ingredient.getName(),
                true);

        if (publicIngredient != null) {
            return publicIngredient;
        }

        var ownIngredient = ingredientRepository.findByNameAndIsPublicIngredientAndOwner(
                ingredient.getName(),
                false,
                user);

        if (ownIngredient != null) {
            return ownIngredient;
        }

        // Make sure a new ingredient is created
        ingredient.setId(null);
        ingredient.setPublicIngredient(false);
        ingredient.setOwner(user);

        return ingredientRepository.save(ingredient);
    }

    public boolean hasPermissionForIngredient(Long id, User user) throws ElementNotFound {
        var ingredient = getIngredient(id);
        if (ingredient.isPublicIngredient()) {
            return true;
        }

        return ingredient.getOwner().equals(user);
    }

    public Ingredient getIngredient(Long id) throws ElementNotFound {
        var optional = ingredientRepository.findById(id);
        if (optional.isEmpty()) {
            throw new ElementNotFound();
        }
        return optional.get();
    }

    public List<Ingredient> getUserPermittedIngredients(User user) {

        var ownIngredients = ingredientRepository.findAllByIsPublicIngredientAndOwner(
                false,
                user);
        var publicIngredients = ingredientRepository.findAllByIsPublicIngredient(true);

        return Stream.concat(publicIngredients.stream(), ownIngredients.stream()).toList();
    }

    public List<Ingredient> getAllIngredients() {
        return ingredientRepository.findAll();
    }

    public void deleteAllIngredientsOfUser(User user) {
        ingredientRepository.deleteAllByOwner(user);
    }
}
