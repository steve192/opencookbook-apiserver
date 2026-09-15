package com.sterul.opencookbookapiserver.unit.services.nutrition.matching;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** A gold names TSV from nutrition-data/gold or nutrition-data/local. */
public final class GoldNames {

    public static final Path REFERENCE_SET = Path.of("nutrition-data", "gold", "names-reference.tsv");
    public static final Path PRODUCTION_SET = Path.of("nutrition-data", "local", "names-production.tsv");

    private static final String NOTHING_FITS = "-";
    /** As the production names export marks names nobody labelled. */
    private static final String UNLABELLED = "?";

    /**
     * @param expected empty when no food fits
     * @param weight   occurrences; 1 in the reference set
     */
    public record GoldName(String typed, String language, Set<String> expected, int weight) {

        public boolean nothingFits() {
            return expected.isEmpty();
        }
    }

    private GoldNames() {
    }

    /** Columns: typed name, language, expected keys ("|"-separated, or "-"), and optionally how often it occurs. */
    public static List<GoldName> read(Path file) {
        try (var lines = Files.lines(file)) {
            return lines
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(GoldNames::parse)
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Gold set " + file + " cannot be read", exception);
        }
    }

    private static GoldName parse(String line) {
        var columns = line.split("\t");
        if (columns.length < 3) {
            throw new IllegalArgumentException("A gold name needs a name, a language and expected keys: " + line);
        }
        if (columns[2].trim().equals(UNLABELLED)) {
            throw new IllegalArgumentException("Label this name with its catalogue keys, or - where no food fits: " + line);
        }
        var expected = columns[2].equals(NOTHING_FITS)
                ? Set.<String>of()
                : Arrays.stream(columns[2].split("\\|")).map(String::trim).collect(Collectors.toUnmodifiableSet());
        var weight = columns.length > 3 ? Integer.parseInt(columns[3].trim()) : 1;
        var language = columns[1].isBlank() ? null : columns[1].trim();
        return new GoldName(columns[0].trim(), language, expected, weight);
    }
}
