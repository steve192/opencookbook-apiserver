package com.sterul.opencookbookapiserver.services.legal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Reads the CycloneDX bills of materials the build puts on the classpath. */
@Component
public class CycloneDxBoms {

    private static final String MAVEN_PURL = "pkg:maven/";

    private final ObjectMapper json = JsonMapper.builder().build();

    /**
     * @param jarName the file a Maven component is packaged as, for finding its own license files; null for npm
     */
    public record BomComponent(String name, String jarName, String license, String licenseUrl, String author,
            String homepage, String licenseText) {
    }

    /** Empty when the build made no such file, as an offline build does. */
    public List<BomComponent> read(String resource) {
        var file = new ClassPathResource(resource);
        if (!file.exists()) {
            return List.of();
        }
        try (var stream = file.getInputStream()) {
            var components = new ArrayList<BomComponent>();
            collect(json.readTree(stream).path("components"), components);
            return components;
        } catch (IOException exception) {
            throw new UncheckedIOException("The bill of materials " + resource + " cannot be read", exception);
        }
    }

    private void collect(JsonNode nodes, List<BomComponent> into) {
        for (var node : nodes) {
            into.add(componentOf(node));
            collect(node.path("components"), into);
        }
    }

    private BomComponent componentOf(JsonNode node) {
        var group = node.path("group").asString("");
        var name = node.path("name").asString("");
        var maven = node.path("purl").asString("").startsWith(MAVEN_PURL);
        var licenses = elements(node.path("licenses"));
        return new BomComponent(
                group.isEmpty() ? name : group + (maven ? ":" : "/") + name,
                maven ? name + "-" + node.path("version").asString("") + ".jar" : null,
                licenses.stream().map(CycloneDxBoms::licenseName).filter(text -> !text.isEmpty()).distinct()
                        .collect(Collectors.joining(" OR ")),
                licenses.stream().map(entry -> entry.path("license").path("url").asString("")).filter(url -> !url.isEmpty())
                        .findFirst().orElse(""),
                firstText(node.path("author"), node.path("publisher"), node.path("supplier").path("name")),
                homepageOf(node),
                texts(node));
    }

    private static String licenseName(JsonNode entry) {
        var license = entry.path("license");
        return firstText(license.path("id"), license.path("name"), entry.path("expression"));
    }

    private static String homepageOf(JsonNode node) {
        var references = elements(node.path("externalReferences"));
        return reference(references, "website").or(() -> reference(references, "vcs")).orElse("");
    }

    private static Optional<String> reference(List<JsonNode> references, String type) {
        return references.stream().filter(reference -> type.equals(reference.path("type").asString("")))
                .map(reference -> reference.path("url").asString("")).findFirst();
    }

    /** The declared licenses' texts, and those found in the package itself (npm's "evidence"). */
    private static String texts(JsonNode node) {
        var texts = new LinkedHashSet<String>();
        for (var entry : elements(node.path("licenses"))) {
            textOf(entry.path("license").path("text")).ifPresent(texts::add);
        }
        for (var entry : elements(node.path("evidence").path("licenses"))) {
            textOf(entry.path("license").path("text")).ifPresent(texts::add);
        }
        return String.join("\n\n", texts);
    }

    private static Optional<String> textOf(JsonNode text) {
        var content = text.path("content").asString("");
        if (content.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("base64".equals(text.path("encoding").asString("")) ?
                new String(Base64.getMimeDecoder().decode(content), StandardCharsets.UTF_8).strip() : content.strip());
    }

    private static String firstText(JsonNode... candidates) {
        for (var candidate : candidates) {
            var text = candidate.asString("");
            if (!text.isBlank()) {
                return text.strip();
            }
        }
        return "";
    }

    private static List<JsonNode> elements(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }
}
