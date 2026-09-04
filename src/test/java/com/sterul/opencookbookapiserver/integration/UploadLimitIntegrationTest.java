package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.unit.DataSize;

/**
 * The upload limits, as an installation sets them.
 *
 * MAX_UPLOAD_SIZE_MB is also what the proxy builds its client_max_body_size from, so these two
 * names are a contract with a file in another repository. Were one of them wrong here, this
 * side would quietly keep its own default while nginx moved, and whichever limit turned out to
 * be the smaller would refuse uploads for a reason neither file explains.
 */
@SpringBootTest(properties = {
        "MAX_UPLOAD_SIZE_MB=60",
        "MAX_IMAGE_SIZE_MB=25",
})
@ActiveProfiles("integration-test")
class UploadLimitIntegrationTest extends IntegrationTest {

    @Autowired
    private MultipartProperties multipart;

    @Test
    void theLimitsComeFromTheNamesTheComposeFilePasses() {
        assertEquals(DataSize.ofMegabytes(60), multipart.getMaxRequestSize());
        assertEquals(DataSize.ofMegabytes(25), multipart.getMaxFileSize());
    }
}
