package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.unit.DataSize;

/** The upload limits an installation that sets neither of them gets. */
@SpringBootTest
@ActiveProfiles("integration-test")
class UploadLimitDefaultsIntegrationTest extends IntegrationTest {

    @Autowired
    private MultipartProperties multipart;

    @Test
    void thePlaceholdersFallBackToTheValuesInApplicationYml() {
        assertEquals(DataSize.ofMegabytes(30), multipart.getMaxRequestSize());
        assertEquals(DataSize.ofMegabytes(10), multipart.getMaxFileSize());
    }
}
