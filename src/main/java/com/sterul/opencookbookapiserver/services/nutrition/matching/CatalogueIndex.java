package com.sterul.opencookbookapiserver.services.nutrition.matching;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;

import com.sterul.opencookbookapiserver.services.nutrition.dataset.NutritionDataset;

/** Immutable in-memory Lucene index over catalogue names; it only finds candidates, the matcher scores them. */
final class CatalogueIndex {

    private static final String ENTRY = "entry";
    private static final String STEMS = "stems";
    private static final String GRAMS = "grams";
    private static final float STEM_BOOST = 3;
    private static final float PART_BOOST = 2;
    private static final float GRAM_BOOST = 0.3f;
    private static final int MOST_GRAMS_PER_WORD = 24;

    record Entry(MatchableFood food, String language, AnalyzedName name) {
    }

    private final NameAnalyzer analyzer;
    private final List<Entry> entries;
    private final Map<String, MatchableFood> foodsByKey;
    private final Map<String, List<MatchableFood>> variantsByBase;
    private final IndexSearcher searcher;

    private CatalogueIndex(NameAnalyzer analyzer, List<Entry> entries, Collection<MatchableFood> foods, IndexSearcher searcher) {
        this.analyzer = analyzer;
        this.entries = List.copyOf(entries);
        this.foodsByKey = foods.stream().collect(Collectors.toUnmodifiableMap(MatchableFood::key, Function.identity()));
        this.variantsByBase = foods.stream().filter(food -> food.variantOf() != null)
                .collect(Collectors.groupingBy(MatchableFood::variantOf));
        this.searcher = searcher;
    }

    static CatalogueIndex build(Collection<MatchableFood> foods, NutritionDataset.Lexicons lexicons, List<String> languages) {
        var analysis = new TextAnalysis(languages);
        var lexiconWords = new LexiconWords(lexicons, analysis);
        var analyzer = vocabularyAnalyzer(foods, analysis, lexiconWords);
        var entries = foods.stream()
                .flatMap(food -> food.names().stream()
                        .map(name -> new Entry(food, name.language(), analyzer.catalogued(name.name(), name.language()))))
                .filter(entry -> !entry.name().words().isEmpty())
                .toList();
        return new CatalogueIndex(analyzer, entries, foods, index(entries));
    }

    NameAnalyzer analyzer() {
        return analyzer;
    }

    Optional<MatchableFood> food(String key) {
        return Optional.ofNullable(foodsByKey.get(key));
    }

    List<MatchableFood> variantsOf(MatchableFood base) {
        return variantsByBase.getOrDefault(base.key(), List.of());
    }

    List<Entry> search(AnalyzedName typed, int limit) {
        var query = new BooleanQuery.Builder();
        for (var word : typed.words()) {
            word.stems().forEach(stem -> query.add(boosted(STEMS, stem, STEM_BOOST), BooleanClause.Occur.SHOULD));
            word.parts().stream().filter(AnalyzedWord.Part::needsExplaining).flatMap(part -> part.stems().stream())
                    .forEach(stem -> query.add(boosted(STEMS, stem, PART_BOOST), BooleanClause.Occur.SHOULD));
            TextAnalysis.grams(word.text()).stream().distinct().limit(MOST_GRAMS_PER_WORD)
                    .forEach(gram -> query.add(boosted(GRAMS, gram, GRAM_BOOST), BooleanClause.Occur.SHOULD));
        }
        try {
            var stored = searcher.storedFields();
            var hits = new ArrayList<Entry>();
            for (var hit : searcher.search(query.build(), limit).scoreDocs) {
                hits.add(entries.get(stored.document(hit.doc).getField(ENTRY).numericValue().intValue()));
            }
            return hits;
        } catch (IOException impossible) {
            throw new UncheckedIOException("The catalogue index lives in memory", impossible);
        }
    }

    /** Every catalogue word may be a compound part; single-word names are food words, even when short. */
    private static NameAnalyzer vocabularyAnalyzer(Collection<MatchableFood> foods, TextAnalysis analysis, LexiconWords lexiconWords) {
        var dictionary = new HashSet<>(lexiconWords.words());
        var foodWordStems = new HashSet<String>();
        var shortFoodWords = new HashSet<String>();
        for (var food : foods) {
            for (var name : food.names()) {
                var words = analysis.cataloguedWords(name.name()).words().stream()
                        .filter(word -> !analysis.isStopword(word) && lexiconWords.state(word).isEmpty())
                        .toList();
                for (var word : words) {
                    dictionary.add(word);
                    dictionary.addAll(analysis.stems(word, name.language()));
                }
                if (words.size() == 1) {
                    foodWordStems.addAll(analysis.stems(words.get(0), name.language()));
                    if (words.get(0).length() < CompoundSplitter.SHORTEST_PART) {
                        shortFoodWords.add(words.get(0));
                    }
                }
            }
        }
        return new NameAnalyzer(analysis, lexiconWords, new CompoundSplitter(dictionary, shortFoodWords), foodWordStems);
    }

    private static IndexSearcher index(List<Entry> entries) {
        var directory = new ByteBuffersDirectory();
        try (var writer = new IndexWriter(directory, new IndexWriterConfig(new WhitespaceAnalyzer()))) {
            for (var position = 0; position < entries.size(); position++) {
                var name = entries.get(position).name();
                var words = name.words();
                var document = new Document();
                document.add(new StoredField(ENTRY, position));
                document.add(new TextField(STEMS, String.join(" ", Stream.concat(words.stream(), name.qualifiers().stream())
                        .flatMap(word -> Stream.concat(word.stems().stream(),
                                word.parts().stream().flatMap(part -> part.stems().stream())))
                        .toList()), Field.Store.NO));
                document.add(new TextField(GRAMS, String.join(" ", words.stream()
                        .flatMap(word -> TextAnalysis.grams(word.text()).stream())
                        .toList()), Field.Store.NO));
                writer.addDocument(document);
            }
            writer.commit();
            return new IndexSearcher(DirectoryReader.open(directory));
        } catch (IOException impossible) {
            throw new UncheckedIOException("The catalogue index lives in memory", impossible);
        }
    }

    private static Query boosted(String field, String term, float boost) {
        return new BoostQuery(new TermQuery(new Term(field, term)), boost);
    }
}
