package com.sterul.opencookbookapiserver.services.recipeimport.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Numbered lines, else bulleted ones, else one per line. Text above the first number or bullet is
 * commentary.
 */
final class Steps {

    private static final Pattern FINISHED = Pattern.compile("[.!?:]$");
    /** "1. Teig anrühren", explained by the lines below it. */
    private static final int MAX_LEAD_WORDS = 4;
    /** Unpunctuated, a longer line runs on into the next. */
    private static final int MAX_TERSE_STEP_WORDS = 4;

    private Steps() {
    }

    static List<String> read(List<String> region) {
        var lines = withoutCommentary(region);
        if (lines.stream().anyMatch(line -> Lines.numbered(line).isPresent())) {
            return items(lines, Lines::numbered);
        }
        if (lines.stream().anyMatch(Lines::hasBullet)) {
            return items(lines, line -> Lines.hasBullet(line) ? Optional.of(Lines.withoutBullet(line)) : Optional.empty());
        }
        return lineByLine(lines);
    }

    private static List<String> withoutCommentary(List<String> region) {
        var lines = new ArrayList<String>();
        for (var line : region) {
            if (line.isBlank()) {
                lines.add("");
                continue;
            }
            var text = withoutClosers(RecipeTextLexicon.stepHeading(line).orElse(line));
            if (!text.isBlank() && !RecipeTextLexicon.isGreeting(text) && !Lines.isQuestion(text)) {
                lines.add(text);
            }
        }
        return lines;
    }

    private static String withoutClosers(String line) {
        return Lines.sentences(Lines.plain(line))
                .filter(sentence -> !RecipeTextLexicon.isCloser(sentence))
                .collect(Collectors.joining(" "));
    }

    private static List<String> items(List<String> lines, Function<String, Optional<String>> itemStart) {
        var steps = new ArrayList<String>();
        String current = null;
        for (var line : lines) {
            if (line.isBlank()) {
                add(steps, current);
                current = null;
                continue;
            }
            var started = itemStart.apply(line);
            if (started.isPresent()) {
                add(steps, current);
                current = started.get();
            } else if (current != null) {
                current = continued(current, Lines.withoutBullet(line));
            }
        }
        add(steps, current);
        return steps;
    }

    private static List<String> lineByLine(List<String> lines) {
        var steps = new ArrayList<String>();
        String current = null;
        for (var line : lines) {
            if (line.isBlank()) {
                add(steps, current);
                current = null;
            } else if (current != null && !FINISHED.matcher(current).find()
                    && Lines.wordCount(current) > MAX_TERSE_STEP_WORDS) {
                current = current + " " + Lines.plain(line);
            } else {
                add(steps, current);
                current = Lines.plain(line);
            }
        }
        add(steps, current);
        return steps;
    }

    private static String continued(String step, String more) {
        if (step.isBlank()) {
            return more;
        }
        var isLead = Lines.wordCount(step) <= MAX_LEAD_WORDS && !FINISHED.matcher(step).find() && !step.contains(":")
                && !step.endsWith(",");
        return step + (isLead ? ": " : " ") + more;
    }

    private static void add(List<String> steps, String step) {
        if (step != null && !step.isBlank()) {
            steps.add(step.trim());
        }
    }
}
