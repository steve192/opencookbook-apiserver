package com.sterul.opencookbookapiserver.services.recipeimport;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class Links {

    private static final Pattern LINK = Pattern.compile(
            "https?://[^\\s<>\"'\\u201C\\u201D\\u2018\\u2019\\u00AB\\u00BB]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.,;:!?]+$");

    private static final int MAX_TITLE_LENGTH = 120;
    private static final String SEPARATORS = "[\\s\\-:|\"'\\u201C\\u201D\\u201E\\u2018\\u2019\\u00AB\\u00BB\\u2013\\u2014]+";
    private static final Pattern SEPARATORS_AT_THE_ENDS = Pattern.compile("^" + SEPARATORS + "|" + SEPARATORS + "$");

    private Links() {
    }

    public static List<String> in(String text) {
        return LINK.matcher(text).results()
                .map(match -> withoutTrailingPunctuation(match.group()))
                .toList();
    }

    public static String without(String text) {
        return LINK.matcher(text).replaceAll("");
    }

    /** The one link in a text that holds at most a title beside it, as browsers share a page. */
    public static Optional<String> shared(String text) {
        var links = in(text);
        if (links.size() != 1) {
            return Optional.empty();
        }
        var besideTheLink = without(text).lines()
                .map(line -> SEPARATORS_AT_THE_ENDS.matcher(line).replaceAll(""))
                .filter(line -> !line.isEmpty())
                .toList();
        var isAtMostATitle = besideTheLink.isEmpty()
                || (besideTheLink.size() == 1 && besideTheLink.get(0).length() <= MAX_TITLE_LENGTH);
        return isAtMostATitle ? Optional.of(links.get(0)) : Optional.empty();
    }

    public static Optional<String> host(String link) {
        try {
            return Optional.ofNullable(new URI(link).getHost())
                    .filter(host -> !host.isBlank())
                    .map(host -> host.toLowerCase(Locale.ROOT));
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Whether the link's host is one of the domains or below one. */
    public static boolean isOn(String link, Set<String> domains) {
        return host(link)
                .filter(host -> domains.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain)))
                .isPresent();
    }

    /** Keeps a bracket the link opened: "https://example.com/pie_(vegan)". */
    private static String withoutTrailingPunctuation(String link) {
        var trimmed = TRAILING_PUNCTUATION.matcher(link).replaceAll("");
        while (trimmed.endsWith(")") && count(trimmed, '(') < count(trimmed, ')')) {
            trimmed = TRAILING_PUNCTUATION.matcher(trimmed.substring(0, trimmed.length() - 1)).replaceAll("");
        }
        return trimmed;
    }

    private static long count(String text, char character) {
        return text.chars().filter(c -> c == character).count();
    }
}
