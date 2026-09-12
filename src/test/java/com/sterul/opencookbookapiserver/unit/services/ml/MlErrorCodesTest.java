package com.sterul.opencookbookapiserver.unit.services.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.services.ml.MlErrorCodes;

/**
 * The subsystem has a vocabulary of its own. Only ours may leave this server, or the app would
 * have to learn both and would silently stop recognising either.
 */
class MlErrorCodesTest {

    @ParameterizedTest
    @CsvSource({
        "QUOTA_EXCEEDED,SCAN_BUSY",
        "TOO_MANY_IN_FLIGHT,SCAN_BUSY",
        "INVALID_TOKEN,SCAN_UNAVAILABLE",
        "TOKEN_REVOKED,SCAN_UNAVAILABLE",
        "TOKEN_EXPIRED,SCAN_UNAVAILABLE",
        "ATTACHMENT_COUNT_INVALID,SCAN_TOO_MANY_PAGES",
        "ATTACHMENT_TOO_LARGE,SCAN_IMAGE_TOO_LARGE",
        "ATTACHMENT_TYPE_UNSUPPORTED,SCAN_IMAGE_UNSUPPORTED",
        "INVALID_PAYLOAD,SCAN_IMAGE_UNREADABLE",
        "OCR_NO_TEXT_FOUND,SCAN_NO_TEXT_FOUND",
        "JOB_ABANDONED,SCAN_TIMED_OUT",
    })
    void everyCodeTheSubsystemDocumentsHasOneOfOurs(String subsystemCode, ApiErrorCode expected) {
        assertEquals(expected, MlErrorCodes.of(subsystemCode));
    }

    /** A subsystem newer than this server must not produce a code the app has never heard of. */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"HANDLER_FAILED", "UNKNOWN_JOB_TYPE", "SOMETHING_ADDED_LATER", ""})
    void anythingElseIsSimplyAScanThatFailed(String subsystemCode) {
        assertEquals(ApiErrorCode.SCAN_FAILED, MlErrorCodes.of(subsystemCode));
    }

    @Test
    void aScanThatIsWorthRetryingIsMarkedSo() {
        assertEquals(true, ApiErrorCode.SCAN_BUSY.isRetryable());
        assertEquals(false, ApiErrorCode.SCAN_NO_TEXT_FOUND.isRetryable());
    }
}
