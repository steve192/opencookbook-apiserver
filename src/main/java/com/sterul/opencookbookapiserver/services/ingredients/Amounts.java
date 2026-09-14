package com.sterul.opencookbookapiserver.services.ingredients;

import java.text.Normalizer;

/** Amounts as recipes write them: "2", "1,5", "1/2", "½", "1½" or "1 1/2". */
public final class Amounts {

    private Amounts() {
    }

    /**
     * @param written the amount alone
     * @throws NumberFormatException if it is no amount
     */
    public static float parse(String written) {
        var amount = written.trim().replace(',', '.');
        if (amount.contains(" ")) {
            var parts = amount.split("\\s+");
            return Float.parseFloat(parts[0]) + fraction(parts[1]);
        }
        if (amount.matches("\\d+\\.*\\d*\\p{No}")) {
            return Float.parseFloat(amount.replaceAll("[^0-9.]", "")) + vulgarFraction(amount.replaceAll("\\d+\\.*\\d*", ""));
        }
        return fraction(amount);
    }

    /** "1/2", "½" or a plain number. */
    private static float fraction(String part) {
        if (part.matches("\\p{No}")) {
            return vulgarFraction(part);
        }
        if (part.contains("/")) {
            var terms = part.split("/");
            return Float.parseFloat(terms[0]) / Float.parseFloat(terms[1]);
        }
        return Float.parseFloat(part);
    }

    /** "½" decomposes to "1⁄2". */
    private static float vulgarFraction(String fraction) {
        var decomposed = Normalizer.normalize(fraction, Normalizer.Form.NFKD);
        if (!decomposed.contains("⁄")) {
            return 0F;
        }
        var terms = decomposed.split("⁄");
        return (float) Integer.parseInt(terms[0]) / Integer.parseInt(terms[1]);
    }
}
