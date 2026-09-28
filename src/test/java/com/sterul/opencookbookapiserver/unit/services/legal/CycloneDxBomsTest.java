package com.sterul.opencookbookapiserver.unit.services.legal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sterul.opencookbookapiserver.services.legal.CycloneDxBoms;

class CycloneDxBomsTest {

    private final List<CycloneDxBoms.BomComponent> components = new CycloneDxBoms().read("legal/example.cdx.json");

    @Test
    void aMavenComponentIsNamedByGroupAndArtifactAndKnowsItsJar() {
        var maven = components.get(0);

        assertEquals("org.example:maven-thing", maven.name());
        assertEquals("maven-thing-1.2.3.jar", maven.jarName());
        assertEquals("Apache-2.0", maven.license());
        assertEquals("https://www.apache.org/licenses/LICENSE-2.0", maven.licenseUrl());
        assertEquals("Example Foundation", maven.author());
        assertEquals("Apache License\nVersion 2.0", maven.licenseText());
    }

    @Test
    void aWebsiteIsPreferredOverTheRepository() {
        assertEquals("https://example.org", components.get(0).homepage());
    }

    @Test
    void anNpmComponentIsNamedByScopeAndCarriesTheLicenseFileItShips() {
        var npm = components.get(1);

        assertEquals("@example/npm-thing", npm.name());
        assertNull(npm.jarName());
        assertEquals("MIT OR MIT OR CC0-1.0", npm.license());
        assertTrue(npm.licenseText().contains("Copyright (c) Example"));
    }

    @Test
    void nestedComponentsAreListedToo() {
        assertEquals(List.of("org.example:maven-thing", "@example/npm-thing", "bundled"),
                components.stream().map(CycloneDxBoms.BomComponent::name).toList());
    }

    @Test
    void aMissingBillOfMaterialsListsNothing() {
        assertEquals(List.of(), new CycloneDxBoms().read("legal/absent.cdx.json"));
    }
}
