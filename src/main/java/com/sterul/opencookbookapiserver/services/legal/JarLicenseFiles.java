package com.sterul.opencookbookapiserver.services.legal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/** The license and notice files a dependency packs into its jar, which Apache-2.0 asks to be passed on. */
@Component
public class JarLicenseFiles {

    private static final String JAR_SUFFIX = ".jar";
    private static final String IN_JAR = JAR_SUFFIX + "!/META-INF/";

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /** @return file contents by jar file name, each file headed by its name */
    public Map<String, String> licenses() {
        return byJar("LICENSE", "license");
    }

    public Map<String, String> notices() {
        return byJar("NOTICE", "notice");
    }

    /** Both spellings: classpath patterns match case, and Spring's own jars write "license.txt". */
    private Map<String, String> byJar(String... names) {
        var byJar = new HashMap<String, String>();
        try {
            for (var file : filesNamed(names)) {
                var jar = jarNameOf(file.getURL().toString());
                if (jar.isPresent()) {
                    byJar.merge(jar.get(), headed(file), (first, second) -> first + "\n\n" + second);
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("The license files on the classpath cannot be read", exception);
        }
        return byJar;
    }

    /** "jar:nested:/app.jar/!BOOT-INF/lib/spring-core-7.0.jar!/META-INF/LICENSE" is spring-core-7.0.jar. */
    public static Optional<String> jarNameOf(String url) {
        var inJar = url.indexOf(IN_JAR);
        if (inJar < 0) {
            return Optional.empty();
        }
        var start = Math.max(url.lastIndexOf('/', inJar), url.lastIndexOf('!', inJar)) + 1;
        return Optional.of(url.substring(start, inJar + JAR_SUFFIX.length()));
    }

    private List<Resource> filesNamed(String... names) throws IOException {
        var files = new ArrayList<Resource>();
        for (var name : names) {
            files.addAll(List.of(resolver.getResources("classpath*:META-INF/" + name + "*")));
        }
        return files;
    }

    private static String headed(Resource file) throws IOException {
        return "===== " + file.getFilename() + " =====\n" + file.getContentAsString(StandardCharsets.UTF_8).strip();
    }
}
