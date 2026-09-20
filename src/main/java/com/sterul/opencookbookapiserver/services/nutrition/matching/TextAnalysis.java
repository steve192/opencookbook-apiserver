package com.sterul.opencookbookapiserver.services.nutrition.matching;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.de.GermanLightStemFilter;
import org.apache.lucene.analysis.de.GermanMinimalStemFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.EnglishMinimalStemFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/** Normalises, tokenises and stems names. Languages without a stemmer compare words as written. */
final class TextAnalysis {

    private static final Pattern BRACKETS = Pattern.compile("\\(([^)]*)\\)|\\[([^]]*)]");
    private static final Pattern APOSTROPHES = Pattern.compile("['’]");
    private static final Pattern NOT_LETTERS = Pattern.compile("[^\\p{L}]+");
    private static final Pattern TOKENS = Pattern.compile("\\p{L}+|\\d+(?:[.,]\\d+)?");
    /** "Zwiebel(n)", "Ei(er)", "Tomate/n". */
    private static final Pattern PLURAL_MARKERS = Pattern.compile("(?<=\\p{L})(?:\\(\\p{L}{1,3}\\)|/\\p{L}{1,2}(?!\\p{L}))");
    private static final int GRAM_LENGTH = 3;
    /** Shorter words are too alike to tell a typo from another word. */
    private static final int SHORTEST_TYPO_WORD = 5;
    private static final int LONGEST_WORD_WITH_ONE_TYPO = 9;

    // German: the light stemmer merges inflections ("gekochte"), the minimal one plurals ("Kartoffeln").
    private static final Map<String, List<Analyzer>> STEMMERS = Map.of(
            "de", List.of(stemmer(GermanLightStemFilter::new), stemmer(GermanMinimalStemFilter::new)),
            "en", List.of(stemmer(EnglishMinimalStemFilter::new)));
    /** The minimal English stemmer leaves "potatoes" as "potatoe"; food plurals in -oes drop the -es. */
    private static final String ENGLISH_OES_PLURAL = "oes";

    private static final Map<String, CharArraySet> STOPWORDS_BY_LANGUAGE = Map.of(
            "de", GermanAnalyzer.getDefaultStopSet(),
            "en", EnglishAnalyzer.getDefaultStopSet());

    private final List<String> languages;
    private final Set<String> stopwords;

    TextAnalysis(List<String> languages) {
        this.languages = List.copyOf(languages);
        this.stopwords = languages.stream()
                .map(language -> STOPWORDS_BY_LANGUAGE.getOrDefault(language, CharArraySet.EMPTY_SET))
                .flatMap(set -> set.stream().map(word -> fold(new String((char[]) word))))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Letters-only words, without brackets; hyphens separate words. */
    List<String> words(String name) {
        return letters(BRACKETS.matcher(normalized(name)).replaceAll(" "));
    }

    /** Words and numbers of a typed name, brackets included, plural markers removed. */
    List<String> typedWords(String name) {
        return tokens(PLURAL_MARKERS.matcher(normalized(name)).replaceAll(""));
    }

    /** Splits a catalogue name into words and qualifiers (numbers, bracketed words). */
    CataloguedWords cataloguedWords(String name) {
        var text = normalized(name);
        var qualifiers = new ArrayList<String>();
        var brackets = BRACKETS.matcher(text);
        while (brackets.find()) {
            qualifiers.addAll(tokens(Objects.requireNonNullElse(brackets.group(1), brackets.group(2))));
        }
        var outside = BRACKETS.matcher(text).replaceAll(" ");
        var words = new ArrayList<String>();
        for (var token : tokens(outside)) {
            (isNumber(token) ? qualifiers : words).add(token);
        }
        return new CataloguedWords(words, qualifiers);
    }

    record CataloguedWords(List<String> words, List<String> qualifiers) {
    }

    static boolean isNumber(String token) {
        return Character.isDigit(token.charAt(0));
    }

    private static String normalized(String name) {
        return APOSTROPHES.matcher(Normalizer.normalize(name, Normalizer.Form.NFKC)).replaceAll("");
    }

    private static List<String> letters(String text) {
        return Arrays.stream(NOT_LETTERS.split(fold(text))).filter(word -> !word.isEmpty()).toList();
    }

    private static List<String> tokens(String text) {
        var tokens = new ArrayList<String>();
        var matcher = TOKENS.matcher(fold(text));
        while (matcher.find()) {
            tokens.add(matcher.group().replace(',', '.'));
        }
        return tokens;
    }

    boolean isStopword(String word) {
        return stopwords.contains(word);
    }

    Set<String> stems(String word, String language) {
        var stemmers = STEMMERS.getOrDefault(language, List.of());
        if (stemmers.isEmpty()) {
            return Set.of(word);
        }
        var stems = new LinkedHashSet<String>();
        stemmers.forEach(stemmer -> stems.add(stem(word, stemmer)));
        if (language.equals("en") && word.endsWith(ENGLISH_OES_PLURAL)) {
            stems.add(word.substring(0, word.length() - 2));
        }
        return stems;
    }

    /** Stems in every supported language, for text of unknown language. */
    Set<String> stems(String word) {
        var stems = new LinkedHashSet<String>();
        languages.forEach(language -> stems.addAll(stems(word, language)));
        return stems;
    }

    /** Edge-marked letter trigrams, for fuzzy lookup. */
    static List<String> grams(String word) {
        var marked = "_" + word + "_";
        if (marked.length() <= GRAM_LENGTH) {
            return List.of(marked);
        }
        return IntStream.rangeClosed(0, marked.length() - GRAM_LENGTH)
                .mapToObj(start -> marked.substring(start, start + GRAM_LENGTH))
                .toList();
    }

    /** 1 - edit distance per letter; 0 beyond a typo (one edit, two in long words) or for short words. */
    static double similarity(String first, String second) {
        var longer = Math.max(first.length(), second.length());
        if (Math.min(first.length(), second.length()) < SHORTEST_TYPO_WORD) {
            return 0;
        }
        var distance = editDistance(first, second);
        var typos = longer > LONGEST_WORD_WITH_ONE_TYPO ? 2 : 1;
        return distance > typos ? 0 : 1 - (double) distance / longer;
    }

    /** Damerau-Levenshtein (optimal string alignment) distance. */
    private static int editDistance(String first, String second) {
        var distances = new int[first.length() + 1][second.length() + 1];
        for (var i = 0; i <= first.length(); i++) {
            distances[i][0] = i;
        }
        for (var j = 0; j <= second.length(); j++) {
            distances[0][j] = j;
        }
        for (var i = 1; i <= first.length(); i++) {
            for (var j = 1; j <= second.length(); j++) {
                var substitution = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                distances[i][j] = Math.min(Math.min(distances[i - 1][j] + 1, distances[i][j - 1] + 1),
                        distances[i - 1][j - 1] + substitution);
                if (i > 1 && j > 1 && first.charAt(i - 1) == second.charAt(j - 2) && first.charAt(i - 2) == second.charAt(j - 1)) {
                    distances[i][j] = Math.min(distances[i][j], distances[i - 2][j - 2] + 1);
                }
            }
        }
        return distances[first.length()][second.length()];
    }

    private static String stem(String word, Analyzer stemmer) {
        try (var stream = stemmer.tokenStream("", word)) {
            var term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            var stem = stream.incrementToken() ? term.toString() : word;
            stream.end();
            return stem;
        } catch (IOException impossible) {
            throw new UncheckedIOException("Stemming a string cannot fail to read", impossible);
        }
    }

    private static String fold(String text) {
        var lower = text.toLowerCase(Locale.ROOT).toCharArray();
        var folded = new char[lower.length * 4];
        var length = ASCIIFoldingFilter.foldToASCII(lower, 0, folded, 0, lower.length);
        return new String(folded, 0, length);
    }

    /** Stems the whole input as one token; thread-safe, as analyzers reuse streams per thread. */
    private static Analyzer stemmer(UnaryOperator<TokenStream> stemFilter) {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                var word = new KeywordTokenizer();
                return new TokenStreamComponents(word, stemFilter.apply(word));
            }
        };
    }
}
