package com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers;

import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.entities.IngredientNeed;
import com.sterul.opencookbookapiserver.services.ingredients.Amounts;
import com.sterul.opencookbookapiserver.services.catalogue.IngredientNames;
import com.sterul.opencookbookapiserver.services.catalogue.UnitLexicon;

/**
 * Splits an ingredient line as a website, a scan or a caption writes it ("1 1/2 EL Butter, weich")
 * into its parts.
 */
@Component
public class IngredientExtractor {

    /** Of a range ("2-3"), the lower bound. */
    private static final String AMOUNT_OR_RANGE = "(" + Amounts.REGEX + ")" + Amounts.OPTIONAL_RANGE_END;
    private static final Pattern AMOUNT = Pattern.compile(AMOUNT_OR_RANGE);
    private static final Pattern WORD_AFTER_AMOUNT = Pattern.compile(AMOUNT_OR_RANGE + "\\s*+([\\p{L}.()]*+)");
    private static final Pattern ADDITIONAL_INFO = Pattern.compile("\\s(\\(.*\\))|,(.*)");

    private final UnitLexicon unitLexicon;

    public IngredientExtractor(UnitLexicon unitLexicon) {
        this.unitLexicon = unitLexicon;
    }

    /** The line as an ingredient of a recipe; empty where it names none. */
    public Optional<IngredientNeed> toNeed(String line) {
        var name = extractName(line);
        if (name.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(IngredientNeed.detached(extractAmount(line), extractUnit(line), name, extractAdditionalInfo(line)));
    }

    /** 0 if there is none. */
    public float extractAmount(String text) {
        var matcher = AMOUNT.matcher(IngredientNames.tidy(text));
        return matcher.find() ? Amounts.parse(matcher.group(1)) : 0f;
    }

    /** The word after the amount if it is a unit, else empty. */
    public String extractUnit(String text) {
        return unit(IngredientNames.tidy(text)).map(unit -> unit.group(2)).orElse("");
    }

    public String extractName(String written) {
        var ingredient = IngredientNames.tidy(written);
        var text = unit(ingredient)
                .map(unit -> ingredient.substring(0, unit.start(2)) + ingredient.substring(unit.end(2)))
                .orElse(ingredient);
        text = AMOUNT.matcher(text).replaceAll("");
        return IngredientNames.tidy(ADDITIONAL_INFO.matcher(text).replaceAll(""));
    }

    public String extractAdditionalInfo(String ingredient) {
        var matcher = ADDITIONAL_INFO.matcher(IngredientNames.tidy(ingredient));
        return matcher.find() ? matcher.group().trim() : "";
    }

    private Optional<MatchResult> unit(String text) {
        var matcher = WORD_AFTER_AMOUNT.matcher(text);
        return matcher.find() && unitLexicon.isKnownUnit(matcher.group(2)) ? Optional.of(matcher.toMatchResult()) : Optional.empty();
    }
}
