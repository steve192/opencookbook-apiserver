package com.sterul.opencookbookapiserver.services.recipeimport.recipescrapers;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sterul.opencookbookapiserver.util.IngredientUnitHelper;

public class IngredientExtractor {
  // Extracts the numeric amount from a string, handling whole numbers, decimals, fractions, and mixed numbers
  public static float extractAmount(String text) {
    text = text.replaceAll(",", "."); // Replace commas with periods for decimal handling

    // Regular expression to capture numbers, including mixed fractions like "1 1/2", "1½", or "1TS"
    Pattern amountPattern = Pattern.compile("(?=(?:\\d\\.*\\d*\\s*)+|(?:\\p{No})+|(?:\\d*\\/\\d*)+)((?:\\d\\.*\\d*\\s*)*(?:\\p{No})*(?:\\d*\\/\\d*)*)");
    Matcher matcher = amountPattern.matcher(text);

    if (matcher.find()) {
        String match = matcher.group().trim();

        // Handle mixed fractions like "1 1/2 or 1½"
        if (match.contains(" ")) {
            String[] parts = match.split(" ");
            float number = Float.parseFloat(parts[0]);
            if (isVulgarFraction(parts[1].trim())) {
                return number + vulgarFractionToFloat(parts[1].trim());
            } else {
                float fraction = extractFraction(parts[1]);
                return number + fraction;
            }
        } else if (match.matches("\\d+\\.*\\d*\\p{No}")) {
            // Handle mixed fractions with vulgar fraction
            float number = Float.parseFloat(match.replaceAll("[^0-9.]", ""));
            float fraction = vulgarFractionToFloat(match.replaceAll("\\d+\\.*\\d*", "").trim());
            return number + fraction;
        } else if (match.contains("/")) {
            // Handle fractions like "1/2"
            return extractFraction(match);
        } else if (isVulgarFraction(match)) {
            // Handle unicode fractions like "½"
            return extractUnicodeFraction(match);
        } else {
            // Handle whole numbers or decimals
            return Float.parseFloat(match);
        }
    }
    return 0f; // Default if no amount is found
}

// Helper method to extract fractions in the form "a/b"
private static float extractFraction(String fraction) {
    String[] parts = fraction.split("/");
    return Float.parseFloat(parts[0]) / Float.parseFloat(parts[1]);
}

// Helper method to extract unicode fractions like "½", "⅓", etc.
private static float extractUnicodeFraction(String unicodeFraction) {
    return vulgarFractionToFloat(unicodeFraction);
}

private static boolean isVulgarFraction(String text) {
    return text.matches("\\p{No}");
}

private static float vulgarFractionToFloat(String vulgarFraction) {
    var normalizedAmount = Normalizer.normalize(vulgarFraction, Normalizer.Form.NFKD);
    if (normalizedAmount.contains("\u2044")) {
        // 2044 = unicode dash
        var fractionParts = normalizedAmount.split("\u2044");
        return (float) Integer.parseInt(fractionParts[0]) / Integer.parseInt(fractionParts[1]);
        
    } else {
        return 0F;
    }
}

// Extracts the unit from a string dynamically without a predefined list of units
public static String extractUnit(String text) {
    var pattern = Pattern.compile("(?=(?:\\d\\.*\\d*\\s*)+|(?:\\p{No})+|(?:\\d*\\/\\d*)+)(?:\\d\\.*\\d*\\s*)*(?:\\p{No})*(?:\\d*\\/\\d*)*\\s*(\\w*)");
    var matcher = pattern.matcher(text);

    if (matcher.find()) {
        var unit = matcher.group().trim();
        if (IngredientUnitHelper.isKnownUnit(unit)) {
            return unit;
        } else {
            return "";
        }
    }
    return "";
}

public static void main(String[] args) {
    String[] testCases = {
        "1 TS Butter",
        "1TS Butter",
        "1TS golden Butter",
        "1 TS Butter, golden",
        "Butter 1 TS",
        "1,5 TS Butter",
        "1.5TS Butter",
        "1 1/2 TS Butter",
        "½ TS Butter",
        "2 ⅔ cups Sugar",
        "2⅔ cups Sugar",
        "¾ cup Milk"
    };

    for (String testCase : testCases) {
        System.out.println("Input: " + testCase);
        float amount = extractAmount(testCase);
        String unit = extractUnit(testCase);

        System.out.println("Amount: " + amount);
        System.out.println("Unit: " + unit);
        System.out.println();
    }
}

private static final String additionalInfoPattern = "\\s(\\(.*\\))";

public static String extractName(String ingredient) {
    // examples
    // Ingredient (details)
    // Ingredient, details
    // Ingredient
    // specifier ingredient
    // specifier ingredient (details)
    // Ingredient(s)
    // TODO Auto-generated method stub
    var unit = extractUnit(ingredient);
    var text = ingredient.replace(unit, "");
    // Remove amount
    text = text.replaceAll("(?=(?:\\d\\.*\\d*\\s*)+|(?:\\p{No})+|(?:\\d*\\/\\d*)+)(?:\\d\\.*\\d*\\s*)*(?:\\p{No})*(?:\\d*\\/\\d*)*", "");
    
    text = text.replaceAll(additionalInfoPattern, "");
    return text.trim();
}

public static String extractAdditionalInfo(String ingredient) {
    var pattern = Pattern.compile(additionalInfoPattern);
    var matcher = pattern.matcher(ingredient);

    if (matcher.find()) {
        return matcher.group().trim();
    }
    return "";
}
}
