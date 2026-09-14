package com.sterul.opencookbookapiserver.services.nutrition.dataset;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Reads the shipped dataset from the classpath; an unreadable file is a broken build. */
@Component
public class NutritionDatasetReader {

    static final String LOCATION = "nutrition/";

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    public NutritionDataset.Manifest manifest() {
        return read("manifest.json", NutritionDataset.Manifest.class);
    }

    public NutritionDataset.Catalogue catalogue() {
        return read("catalogue.json", NutritionDataset.Catalogue.class);
    }

    public NutritionDataset.Units units() {
        return read("units.json", NutritionDataset.Units.class);
    }

    public NutritionDataset.States states() {
        return read("states.json", NutritionDataset.States.class);
    }

    public NutritionDataset.Lexicons lexicons() {
        return read("lexicons.json", NutritionDataset.Lexicons.class);
    }

    /** SHA-256 over the manifest's files, as the build computes it. */
    public String computeChecksum() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var file : manifest().files()) {
                try (var stream = open(file)) {
                    digest.update(stream.readAllBytes());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", exception);
        }
    }

    /** Also for shipped files other packages own, such as the matcher weights. */
    public <T> T read(String file, Class<T> type) {
        try (var stream = open(file)) {
            return objectMapper.readValue(stream, type);
        } catch (IOException exception) {
            throw new UncheckedIOException("The nutrition dataset file " + file + " cannot be read", exception);
        }
    }

    private InputStream open(String file) throws IOException {
        return new ClassPathResource(LOCATION + file).getInputStream();
    }
}
