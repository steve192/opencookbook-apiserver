package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sterul.opencookbookapiserver.cronjobs.BringExportDeletionJob;
import com.sterul.opencookbookapiserver.cronjobs.IngredientDeletionJob;
import com.sterul.opencookbookapiserver.cronjobs.MlJobPollingCronjob;
import com.sterul.opencookbookapiserver.repositories.RecipeRepository;
import com.sterul.opencookbookapiserver.services.nutrition.IngredientNames;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.LineFlag;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.LineNutrition;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.LineStatus;
import com.sterul.opencookbookapiserver.services.nutrition.calculation.NutritionCalculator;
import com.sterul.opencookbookapiserver.services.nutrition.catalogue.NutritionDatasetImporter;
import com.sterul.opencookbookapiserver.services.nutrition.matching.CatalogueIndexUpdater;
import com.sterul.opencookbookapiserver.services.nutrition.reports.CoverageReport;
import com.sterul.opencookbookapiserver.services.nutrition.reports.IngredientNameReport;

/**
 * Writes the admin reports of a local database copy to nutrition-data/local/ (never committed). Read-only; skips
 * itself without a database:
 *
 * <pre>
 * mvn test -DskipAdminUi -Plocal-instance-reports -Dlocal.instance.url=jdbc:postgresql://localhost:5432/cookpal \
 *     -Dlocal.instance.username=... -Dlocal.instance.password=...
 * </pre>
 */
@Tag("local-instance")
@SpringBootTest(properties = {
        "opencookbook.nutrition.enabled=true",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class LocalInstanceReportsTest {

    private static final String URL = System.getProperty("local.instance.url");
    private static final Path LOCAL = Path.of("nutrition-data", "local");
    private static final int UNMATCHED_NAMES = 1000;

    @MockitoBean
    private NutritionDatasetImporter importer;
    @MockitoBean
    private MlJobPollingCronjob mlJobPolling;
    @MockitoBean
    private IngredientDeletionJob ingredientDeletion;
    @MockitoBean
    private BringExportDeletionJob bringExportDeletion;

    @Autowired
    private CatalogueIndexUpdater indexUpdater;
    @Autowired
    private IngredientNameReport nameReport;
    @Autowired
    private CoverageReport coverageReport;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private NutritionCalculator calculator;

    private TransactionTemplate readOnly;

    @Autowired
    void readOnlyTransactions(PlatformTransactionManager transactionManager) {
        readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
    }

    @DynamicPropertySource
    static void localDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL == null ? "jdbc:postgresql://localhost:1/none" : URL);
        registry.add("spring.datasource.username", () -> System.getProperty("local.instance.username", "cookpal"));
        registry.add("spring.datasource.password", () -> System.getProperty("local.instance.password", ""));
    }

    @Test
    @SuppressWarnings("java:S2699") // A report writer run through Maven, not a check: the files are its outcome.
    void writeTheReports() throws IOException {
        assumeTrue(URL != null, "no local instance given with -Dlocal.instance.url");
        indexUpdater.rebuild();

        Files.writeString(LOCAL.resolve("names-production-export.tsv"), nameReport.productionNames());
        Files.writeString(LOCAL.resolve("coverage-report.txt"), coverage());
        Files.writeString(LOCAL.resolve("unmatched-names.tsv"), unmatchedNames());
        Files.writeString(LOCAL.resolve("warning-lines.tsv"), readOnly.execute(status -> warningLines()));
        Files.writeString(LOCAL.resolve("warning-causes-per-recipe.tsv"), readOnly.execute(status -> warningCausesPerRecipe()));
    }

    /** How many recipes fixing each combination of warning causes would complete. */
    private String warningCausesPerRecipe() {
        var groups = recipeRepository.findAllWithIngredients().stream()
                .map(recipe -> calculator.calculate(recipe).lines().stream()
                        .filter(LineNutrition::warns)
                        .map(line -> line.status() != LineStatus.RESOLVED ? line.status().name()
                                : line.flags().contains(LineFlag.LOW_CONFIDENCE) ? "LOW_CONFIDENCE" : "GUESSED_AMOUNT")
                        .collect(Collectors.toCollection(TreeSet::new)))
                .filter(causes -> !causes.isEmpty())
                .collect(Collectors.groupingBy(Object::toString, Collectors.counting()));
        return "# causes\trecipes\n" + groups.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> entry.getKey() + "\t" + entry.getValue())
                .collect(Collectors.joining("\n")) + "\n";
    }

    /** Warning lines grouped by why they warn, their ingredient, unit and food: what a curation fixes at once. */
    private String warningLines() {
        var groups = recipeRepository.findAllWithIngredients().stream()
                .flatMap(recipe -> calculator.calculate(recipe).lines().stream())
                .filter(LineNutrition::warns)
                .collect(Collectors.groupingBy(line -> String.join("\t",
                        line.status() == LineStatus.RESOLVED ? line.flags().toString() : line.status().name(),
                        IngredientNames.normalise(line.need().getIngredient().getName()),
                        String.valueOf(line.need().getUnit()),
                        line.need().getIngredient().getCatalogueFood() == null ? "-"
                                : line.need().getIngredient().getCatalogueFood().getCatalogueKey()),
                        Collectors.counting()));
        return "# cause\tname\tunit\tfood\tlines\n" + groups.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> entry.getKey() + "\t" + entry.getValue())
                .collect(Collectors.joining("\n")) + "\n";
    }

    private String coverage() {
        var coverage = coverageReport.coverage();
        return "recipes\t" + coverage.recipeCount() + "\n"
                + coverage.recipesByStatus().entrySet().stream().map(entry -> entry.getKey() + "\t" + entry.getValue())
                        .collect(Collectors.joining("\n")) + "\n"
                + "lines\t" + coverage.lineCount() + "\nwarning lines\t" + coverage.warningLineCount() + "\n\n# why lines warn\n"
                + coverage.warningCauses().stream().map(count -> count.key() + "\t" + count.count()).collect(Collectors.joining("\n"))
                + "\n\n# names warning most\n"
                + coverage.namesCausingWarnings().stream().map(count -> count.key() + "\t" + count.count()).collect(Collectors.joining("\n"))
                + "\n";
    }

    private String unmatchedNames() {
        return "# name\tlanguage\tusers\trecipe lines\tcandidates (key confidence)\n" + nameReport.unmatchedNames(UNMATCHED_NAMES).stream()
                .map(name -> String.join("\t", name.name(), String.valueOf(name.language()), String.valueOf(name.userCount()),
                        String.valueOf(name.useCount()), name.candidates().stream()
                                .map(candidate -> candidate.food().getCatalogueKey() + " " + Math.round(candidate.confidence() * 100) + "%")
                                .collect(Collectors.joining(", "))))
                .collect(Collectors.joining("\n")) + "\n";
    }
}
