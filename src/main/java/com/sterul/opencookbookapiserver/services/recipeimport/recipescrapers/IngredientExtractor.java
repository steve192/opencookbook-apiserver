package com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.services.ingredients.Amounts;
import com.sterul.opencookbookapiserver.services.nutrition.UnitLexicon;

/** Splits an ingredient line as a website or a scan writes it ("1 1/2 EL Butter, weich") into its parts. */
@Component
public class IngredientExtractor {

    private static final String AMOUNT_REGEX = "(?=(?:\\d\\.*\\d*\\s*)+|(?:\\p{No})+|(?:\\d*\\/\\d*)+)(?:\\d\\.*\\d*\\s*)*(?:\\p{No})*(?:\\d*\\/\\d*)*";
    private static final Pattern AMOUNT = Pattern.compile(AMOUNT_REGEX);
    private static final Pattern UNIT = Pattern.compile(AMOUNT_REGEX + "\\s*([\\w.()]*)");
    private static final Pattern ADDITIONAL_INFO = Pattern.compile("\\s(\\(.*\\))|\\,(.*)");

    private final UnitLexicon unitLexicon;

    public IngredientExtractor(UnitLexicon unitLexicon) {
        this.unitLexicon = unitLexicon;
    }

    /** 0 if there is none. */
    public float extractAmount(String text) {
        var matcher = AMOUNT.matcher(text.replace(',', '.'));
        return matcher.find() ? Amounts.parse(matcher.group()) : 0f;
    }

    public String extractUnit(String text) {
        var matcher = UNIT.matcher(text);
        if (matcher.find()) {
            var unit = matcher.group(1).trim();
            return unitLexicon.isKnownUnit(unit) ? unit : "";
        }
        return "";
    }

    public String extractName(String ingredient) {
        var text = ingredient.replace(extractUnit(ingredient), "");
        text = AMOUNT.matcher(text).replaceAll("");
        text = ADDITIONAL_INFO.matcher(text).replaceAll("");
        return text.trim();
    }

    public String extractAdditionalInfo(String ingredient) {
        var matcher = ADDITIONAL_INFO.matcher(ingredient);
        return matcher.find() ? matcher.group().trim() : "";
    }
}
