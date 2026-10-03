package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sterul.opencookbookapiserver.services.instance.InstanceChecks;
import com.sterul.opencookbookapiserver.services.instance.InstanceChecks.Status;

/** Services that accept a connection and never answer must not hold the checks past their limit. */
@SpringBootTest(properties = {
        "opencookbook.instanceURL=https://cookbook.example.com",
        "opencookbook.smtp-protocol=smtp",
        "opencookbook.ml.api-token=a-token",
        "opencookbook.ml.connect-timeout-seconds=60",
        "opencookbook.ml.request-timeout-seconds=60",
})
@ActiveProfiles("integration-test")
class InstanceChecksTimeoutIntegrationTest extends IntegrationTestBase {

    /** The connection is accepted by the operating system; nobody ever reads from it. */
    private static final ServerSocket SILENT_SERVER = silentServer();

    @Autowired
    private InstanceChecks checks;

    @DynamicPropertySource
    static void pointEveryServiceAtTheSilentServer(DynamicPropertyRegistry registry) {
        var url = "http://127.0.0.1:" + SILENT_SERVER.getLocalPort();
        registry.add("opencookbook.smtp-host", () -> "127.0.0.1");
        registry.add("opencookbook.smtp-port", SILENT_SERVER::getLocalPort);
        registry.add("opencookbook.recipe-scaper-service-url", () -> url);
        registry.add("opencookbook.ml.service-url", () -> url);
    }

    @AfterAll
    static void close() throws IOException {
        SILENT_SERVER.close();
    }

    @Test
    void everyCheckGivesUpByItselfWithinTheLimit() {
        var started = System.nanoTime();
        var results = checks.runAll();
        var took = Duration.ofNanos(System.nanoTime() - started);

        assertEquals(3, results.size());
        for (var result : results) {
            assertEquals(Status.FAILED, result.status(), result.toString());
            assertNotNull(result.detail());
            assertFalse(result.detail().startsWith("No answer within"), "the safety net had to step in: " + result);
        }
        assertTrue(took.compareTo(Duration.ofSeconds(5)) < 0, "took " + took);
    }

    private static ServerSocket silentServer() {
        try {
            return new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
