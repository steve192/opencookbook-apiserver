package com.sterul.opencookbookapiserver.unit.services.legal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

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

    private static String valueOfJarStartingWith(Map<String, String> byJar, String prefix) {
        return byJar.entrySet().stream().filter(entry -> entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue).findFirst().orElse("");
    }
}
