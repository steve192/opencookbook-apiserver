package com.sterul.opencookbookapiserver.services.catalogue.dataset;

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
public class CatalogueDatasetReader {

    static final String LOCATION = "catalogue/";

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    /** Read once: every nutrition sheet carries its attributions. */
    private final CatalogueDataset.Manifest manifest = read("manifest.json", CatalogueDataset.Manifest.class);

    public CatalogueDataset.Manifest manifest() {
        return manifest;
    }

    public CatalogueDataset.Catalogue catalogue() {
        return read("catalogue.json", CatalogueDataset.Catalogue.class);
    }

    public CatalogueDataset.Units units() {
        return read("units.json", CatalogueDataset.Units.class);
    }

    public CatalogueDataset.States states() {
        return read("states.json", CatalogueDataset.States.class);
    }

    public CatalogueDataset.Lexicons lexicons() {
        return read("lexicons.json", CatalogueDataset.Lexicons.class);
    }

    public CatalogueDataset.NonFoodItems nonFood() {
        return read("non-food.json", CatalogueDataset.NonFoodItems.class);
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
            throw new UncheckedIOException("The catalogue dataset file " + file + " cannot be read", exception);
        }
    }

    private InputStream open(String file) throws IOException {
        return new ClassPathResource(LOCATION + file).getInputStream();
    }
}
