package com.sterul.opencookbookapiserver.unit.services.legal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.services.legal.JarLicenseFiles;

class JarLicenseFilesTest {

    private final JarLicenseFiles files = new JarLicenseFiles();

    @Test
    void findsTheNoticeSpringPacksInLowerCase() {
        assertTrue(valueOfJarStartingWith(files.notices(), "spring-core-").contains("===== notice.txt ====="));
    }

    @Test
    void findsTheLicenseOfAJar() {
        assertTrue(valueOfJarStartingWith(files.licenses(), "spring-core-").contains("Apache License"));
    }

    @Test
    void namesTheJarNestedInsideTheApplication() {
        assertEquals(Optional.of("spring-core-7.0.1.jar"), JarLicenseFiles.jarNameOf(
                "jar:nested:/app/opencookbook.jar/!BOOT-INF/lib/spring-core-7.0.1.jar!/META-INF/LICENSE.txt"));
    }

    @Test
    void namesAJarOnTheFileSystem() {
        assertEquals(Optional.of("jackson-core-3.0.jar"),
                JarLicenseFiles.jarNameOf("jar:file:/home/me/.m2/jackson-core-3.0.jar!/META-INF/LICENSE"));
    }

    @Test
    void ignoresAFileOutsideAJar() {
        assertEquals(Optional.empty(), JarLicenseFiles.jarNameOf("file:/app/classes/META-INF/LICENSE"));
    }

    private static String valueOfJarStartingWith(Map<String, String> byJar, String prefix) {
        return byJar.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue).findFirst().orElse("");
    }
}
