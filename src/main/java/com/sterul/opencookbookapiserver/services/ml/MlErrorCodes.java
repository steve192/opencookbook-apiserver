package com.sterul.opencookbookapiserver.services.ml;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;

/**
 * Translates the subsystem's error codes into ours, so only one vocabulary leaves this server
 * and a code it adds later still reads as a scan that failed rather than as a mystery.
 */
public final class MlErrorCodes {

    private MlErrorCodes() {
    }

    public static ApiErrorCode of(String subsystemCode) {
        if (subsystemCode == null) {
            return ApiErrorCode.SCAN_FAILED;
        }
        // Names from the subsystem's own ErrorCode enum. Ours never come through here.
        return switch (subsystemCode) {
            case "QUOTA_EXCEEDED", "TOO_MANY_IN_FLIGHT" -> ApiErrorCode.SCAN_BUSY;
            case "INVALID_TOKEN", "TOKEN_REVOKED", "TOKEN_EXPIRED" -> ApiErrorCode.SCAN_UNAVAILABLE;
            case "ATTACHMENT_COUNT_INVALID" -> ApiErrorCode.SCAN_TOO_MANY_PAGES;
            case "ATTACHMENT_TOO_LARGE" -> ApiErrorCode.SCAN_IMAGE_TOO_LARGE;
            case "ATTACHMENT_TYPE_UNSUPPORTED" -> ApiErrorCode.SCAN_IMAGE_UNSUPPORTED;
            case "INVALID_PAYLOAD" -> ApiErrorCode.SCAN_IMAGE_UNREADABLE;
            case "OCR_NO_TEXT_FOUND" -> ApiErrorCode.SCAN_NO_TEXT_FOUND;
            case "JOB_ABANDONED" -> ApiErrorCode.SCAN_TIMED_OUT;
            default -> ApiErrorCode.SCAN_FAILED;
        };
    }
}
