package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;

/**
 * A value somebody sent must not be able to become two log entries. Handled once in the log
 * pattern, so what is tested here is the appender the application actually runs with.
 */
@SpringBootTest
@ActiveProfiles("integration-test")
class LogForgingIntegrationTest extends IntegrationTest {

    private static final String FORGED =
            "victim@example.com\n2026-01-01T00:00:00.000+02:00  INFO 1 --- [x] x : all is well";

    @Test
    void aValueCannotEndTheLogEntryItIsWrittenIn() {
        assertEquals(1, render().strip().lines().count(),
                "a submitted newline got through the log pattern");
    }

    @Test
    void theValueItselfIsStillReadable() {
        var line = render();

        assertTrue(line.contains("victim@example.com"), "the address was lost entirely");
        assertFalse(line.contains("\n2026-01-01"), "the forged entry survived intact");
    }

    /** Formats one event through the configured console appender. */
    private String render() {
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        var event = new LoggingEvent(Logger.FQCN, context.getLogger(getClass()), Level.INFO,
                "Creating user for {}", null, new Object[] {FORGED});

        var appender = (OutputStreamAppender<ILoggingEvent>)
                context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("CONSOLE");
        var encoder = (LayoutWrappingEncoder<ILoggingEvent>) appender.getEncoder();
        return encoder.getLayout().doLayout(event);
    }
}
