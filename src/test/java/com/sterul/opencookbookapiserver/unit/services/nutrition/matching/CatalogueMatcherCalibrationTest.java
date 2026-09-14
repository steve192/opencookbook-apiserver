package com.sterul.opencookbookapiserver.unit.services.nutrition.matching;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.services.nutrition.matching.MatchCandidate;
import com.sterul.opencookbookapiserver.services.nutrition.matching.MatchFeatures;
import com.sterul.opencookbookapiserver.services.nutrition.matching.MatcherWeights;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fits matcher-weights.json to the gold sets: {@code mvn test -Pcalibrate-matcher -DskipAdminUi}.
 * First the ranking (conditional logit), then the confidence (logistic regression on first candidates).
 * Writes only weights at least as good as the shipped ones.
 */
@Tag("calibration")
class CatalogueMatcherCalibrationTest {

    private static final Path WEIGHTS = Path.of("src", "main", "resources", "nutrition", "matcher-weights.json");
    private static final int CANDIDATES_PER_NAME = 10;
    private static final int ROUNDS = 3;
    private static final int NEWTON_STEPS = 30;
    private static final int MOST_STEP_HALVINGS = 30;
    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
    /** Keeps weights of rarely varying features from growing without bound. */
    private static final double L2 = 1.0;

    /** The ranking features, in the order of {@link MatcherWeights} after the bias. */
    private static final List<ToDoubleFunction<MatchFeatures>> RANKING = List.of(
            MatchFeatures::queryCoverage, MatchFeatures::candidateCoverage, MatchFeatures::exact, MatchFeatures::fuzzyShare,
            MatchFeatures::unexplainedWords, MatchFeatures::conflictingFoodWords, MatchFeatures::otherFoodWords,
            MatchFeatures::missingStates, MatchFeatures::extraStates, MatchFeatures::otherLanguage);

    /** A gold name's candidates: their ranking features, and which of them are right. */
    private record Competition(List<double[]> features, List<Boolean> right, int weight) {
    }

    @Test
    void fitWeightsToTheGoldSets() throws IOException {
        var names = new ArrayList<>(GoldNames.read(GoldNames.REFERENCE_SET));
        if (Files.exists(GoldNames.PRODUCTION_SET)) {
            names.addAll(GoldNames.read(GoldNames.PRODUCTION_SET));
        }

        // From the source tree rather than the classpath, which may still hold weights of an earlier build.
        var shipped = JSON.readValue(Files.readString(WEIGHTS), MatcherWeights.class);
        var before = MatcherEvaluation.of(ShippedCatalogueMatcher.withWeights(shipped), names);
        System.out.println("Shipped: " + before);

        var weights = shipped;
        for (var round = 0; round < ROUNDS; round++) {
            var competitions = competitions(names, weights);
            var ranking = fitRanking(competitions, rankingWeights(weights));
            weights = fitConfidence(competitions, ranking, weights);
            System.out.println("Round " + (round + 1) + ": " + MatcherEvaluation.of(ShippedCatalogueMatcher.withWeights(weights), names));
        }

        var after = MatcherEvaluation.of(ShippedCatalogueMatcher.withWeights(weights), names);
        System.out.println("Fitted: " + weights + "\n" + after + "\n" + String.join("\n", after.misses()));
        assertTrue(after.silentPrecision() >= before.silentPrecision() && after.coverage() >= before.coverage(),
                "the fitted weights do worse than the shipped ones and are not written: " + after + " against " + before);
        Files.writeString(WEIGHTS, JSON.writeValueAsString(rounded(weights)) + "\n");
    }

    private static List<Competition> competitions(List<GoldNames.GoldName> names, MatcherWeights weights) {
        var matcher = ShippedCatalogueMatcher.withWeights(weights);
        var competitions = new ArrayList<Competition>();
        for (var name : names) {
            List<MatchCandidate> candidates = matcher.rank(name.typed(), name.language(), CANDIDATES_PER_NAME);
            competitions.add(new Competition(
                    candidates.stream().map(candidate -> ranking(candidate.features())).toList(),
                    candidates.stream().map(candidate -> name.expected().contains(candidate.foodKey())).toList(),
                    name.weight()));
        }
        return competitions;
    }

    /** Fits the ranking weights as a conditional logit: the right candidate of each name against its rivals. */
    private static double[] fitRanking(List<Competition> competitions, double[] start) {
        var ranked = competitions.stream().filter(competition -> competition.right().contains(true)).toList();
        return maximise(start, coefficients -> {
            var size = coefficients.length;
            var value = 0.0;
            var gradient = new double[size];
            var hessian = new double[size][size];
            for (var competition : ranked) {
                var scores = competition.features().stream().mapToDouble(x -> dot(coefficients, x)).toArray();
                var logSum = logSumExp(scores);
                var expected = new double[size];
                var second = new double[size][size];
                for (var c = 0; c < scores.length; c++) {
                    var p = Math.exp(scores[c] - logSum);
                    var x = competition.features().get(c);
                    for (var i = 0; i < size; i++) {
                        expected[i] += p * x[i];
                        for (var j = 0; j < size; j++) {
                            second[i][j] += p * x[i] * x[j];
                        }
                    }
                }
                var right = competition.right().indexOf(true);
                value += competition.weight() * (scores[right] - logSum);
                var x = competition.features().get(right);
                for (var i = 0; i < size; i++) {
                    gradient[i] += competition.weight() * (x[i] - expected[i]);
                    for (var j = 0; j < size; j++) {
                        hessian[i][j] -= competition.weight() * (second[i][j] - expected[i] * expected[j]);
                    }
                }
            }
            return regularised(value, gradient, hessian, coefficients, 0);
        });
    }

    /** The margin depends on scale and bias, so it is recomputed each iteration. */
    private static MatcherWeights fitConfidence(List<Competition> competitions, double[] ranking, MatcherWeights previous) {
        var calibration = new double[] {1, previous.bias(), previous.margin()};
        for (var settle = 0; settle < ROUNDS; settle++) {
            var scale = calibration[0];
            var bias = calibration[1];
            var samples = new ArrayList<double[]>();
            var labels = new ArrayList<Boolean>();
            var weights = new ArrayList<Integer>();
            for (var competition : competitions) {
                if (competition.features().isEmpty()) {
                    continue;
                }
                var scores = competition.features().stream().mapToDouble(x -> dot(ranking, x)).sorted().toArray();
                var first = argmax(competition.features(), ranking);
                var best = scores[scores.length - 1];
                var runnerUp = scores.length > 1 ? scores[scores.length - 2] : Double.NEGATIVE_INFINITY;
                var margin = logistic(scale * best + bias) - (scores.length > 1 ? logistic(scale * runnerUp + bias) : 0);
                samples.add(new double[] {best, 1, margin});
                labels.add(competition.right().get(first));
                weights.add(competition.weight());
            }
            calibration = maximise(calibration, coefficients -> {
                var value = 0.0;
                var gradient = new double[3];
                var hessian = new double[3][3];
                for (var s = 0; s < samples.size(); s++) {
                    var x = samples.get(s);
                    var z = dot(coefficients, x);
                    var p = logistic(z);
                    var weight = weights.get(s);
                    value += weight * (labels.get(s) ? logSigmoid(z) : logSigmoid(z) - z);
                    for (var i = 0; i < 3; i++) {
                        gradient[i] += weight * ((labels.get(s) ? 1 : 0) - p) * x[i];
                        for (var j = 0; j < 3; j++) {
                            hessian[i][j] -= weight * p * (1 - p) * x[i] * x[j];
                        }
                    }
                }
                // Only the margin weight is regularised; scale and bias carry the whole score.
                return regularised(value, gradient, hessian, coefficients, 2);
            });
        }
        var c = new double[12];
        c[0] = calibration[1];
        for (var i = 0; i < ranking.length; i++) {
            c[i + 1] = calibration[0] * ranking[i];
        }
        c[11] = calibration[2];
        return weights(c);
    }

    /** A value to maximise at given coefficients, with its gradient and Hessian. */
    private record Objective(double value, double[] gradient, double[][] hessian) {
    }

    private interface Differentiable {
        Objective at(double[] coefficients);
    }

    /** Newton's method, halving a step until it improves the objective so that it cannot overshoot. */
    private static double[] maximise(double[] start, Differentiable objective) {
        var coefficients = start.clone();
        for (var step = 0; step < NEWTON_STEPS; step++) {
            var here = objective.at(coefficients);
            var change = solve(here.hessian(), here.gradient());
            var length = 1.0;
            for (var halving = 0; halving < MOST_STEP_HALVINGS; halving++, length /= 2) {
                var candidate = coefficients.clone();
                for (var i = 0; i < candidate.length; i++) {
                    candidate[i] -= length * change[i];
                }
                if (objective.at(candidate).value() >= here.value()) {
                    coefficients = candidate;
                    break;
                }
            }
        }
        return coefficients;
    }

    /** Adds an L2 penalty on the coefficients from the given index on. */
    private static Objective regularised(double value, double[] gradient, double[][] hessian, double[] coefficients, int from) {
        for (var i = from; i < coefficients.length; i++) {
            value -= L2 / 2 * coefficients[i] * coefficients[i];
            gradient[i] -= L2 * coefficients[i];
            hessian[i][i] -= L2;
        }
        return new Objective(value, gradient, hessian);
    }

    /** Solves a * x = b by Gaussian elimination with partial pivoting. */
    private static double[] solve(double[][] a, double[] b) {
        var n = b.length;
        var m = new double[n][n + 1];
        for (var i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, m[i], 0, n);
            m[i][n] = b[i];
        }
        for (var column = 0; column < n; column++) {
            var pivot = column;
            for (var row = column + 1; row < n; row++) {
                if (Math.abs(m[row][column]) > Math.abs(m[pivot][column])) {
                    pivot = row;
                }
            }
            var swap = m[column];
            m[column] = m[pivot];
            m[pivot] = swap;
            for (var row = column + 1; row < n; row++) {
                var factor = m[row][column] / m[column][column];
                for (var k = column; k <= n; k++) {
                    m[row][k] -= factor * m[column][k];
                }
            }
        }
        var x = new double[n];
        for (var row = n - 1; row >= 0; row--) {
            var sum = m[row][n];
            for (var k = row + 1; k < n; k++) {
                sum -= m[row][k] * x[k];
            }
            x[row] = sum / m[row][row];
        }
        return x;
    }

    private static double[] ranking(MatchFeatures features) {
        return RANKING.stream().mapToDouble(feature -> feature.applyAsDouble(features)).toArray();
    }

    private static double[] rankingWeights(MatcherWeights w) {
        return new double[] {w.queryCoverage(), w.candidateCoverage(), w.exact(), w.fuzzyShare(), w.unexplainedWords(),
                w.conflictingFoodWords(), w.otherFoodWords(), w.missingStates(), w.extraStates(), w.otherLanguage()};
    }

    private static MatcherWeights weights(double[] c) {
        return new MatcherWeights(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9], c[10], c[11]);
    }

    private static MatcherWeights rounded(MatcherWeights w) {
        var c = new double[] {w.bias(), w.queryCoverage(), w.candidateCoverage(), w.exact(), w.fuzzyShare(),
                w.unexplainedWords(), w.conflictingFoodWords(), w.otherFoodWords(), w.missingStates(), w.extraStates(),
                w.otherLanguage(), w.margin()};
        for (var i = 0; i < c.length; i++) {
            c[i] = Math.round(c[i] * 1000) / 1000.0;
        }
        return weights(c);
    }

    private static int argmax(List<double[]> features, double[] coefficients) {
        var best = 0;
        for (var c = 1; c < features.size(); c++) {
            if (dot(coefficients, features.get(c)) > dot(coefficients, features.get(best))) {
                best = c;
            }
        }
        return best;
    }

    private static double dot(double[] first, double[] second) {
        var sum = 0.0;
        for (var i = 0; i < first.length; i++) {
            sum += first[i] * second[i];
        }
        return sum;
    }

    private static double logSumExp(double[] values) {
        var max = Double.NEGATIVE_INFINITY;
        for (var value : values) {
            max = Math.max(max, value);
        }
        var sum = 0.0;
        for (var value : values) {
            sum += Math.exp(value - max);
        }
        return max + Math.log(sum);
    }

    /** log(sigmoid(z)), without overflow for large |z|. */
    private static double logSigmoid(double z) {
        return Math.min(z, 0) - Math.log1p(Math.exp(-Math.abs(z)));
    }

    private static double logistic(double logOdds) {
        return 1 / (1 + Math.exp(-logOdds));
    }
}
