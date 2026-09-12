package com.sterul.opencookbookapiserver.errors;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Everything a client is told about a failure. The code is the contract clients switch on; the
 * message is a vague English fallback and never describes the inside of the server.
 *
 * Outside the controller packages because services raise these. The app has one message per
 * code in its MESSAGE_KEYS table, so a code added here needs one added there.
 */
@Getter
public enum ApiErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "One or more fields are not valid"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be understood"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "That is not how this endpoint is called"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "The request was not sent in a format this endpoint reads"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE,
            "This endpoint cannot answer in any format that was asked for"),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "The file is not of a supported type"),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "The file is larger than this server accepts"),

    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "Sign in to do that"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "The e-mail address or the password is wrong"),
    ACCOUNT_NOT_ACTIVATED(HttpStatus.UNAUTHORIZED, "The account has not been activated yet"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You are not allowed to do that"),

    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "That e-mail address is already registered"),
    SIGNUP_DISABLED(HttpStatus.FORBIDDEN, "This instance does not accept new accounts"),
    ACTIVATION_LINK_INVALID(HttpStatus.NOT_FOUND, "The activation link is not valid any more"),
    PASSWORD_RESET_LINK_INVALID(HttpStatus.NOT_FOUND,
            "The password reset link is not valid any more"),
    LAST_ADMINISTRATOR(HttpStatus.CONFLICT,
            "The instance would be left without an administrator who can sign in"),

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "That does not exist, or is not yours"),
    CONFLICT(HttpStatus.CONFLICT, "That conflicts with something that already exists"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please try again later", true),
    MAIL_DELIVERY_FAILED(HttpStatus.BAD_GATEWAY, "The e-mail could not be sent", true),

    IMPORT_URL_INVALID(HttpStatus.BAD_REQUEST, "That is not a link this server can read"),
    IMPORT_NOT_SUPPORTED(HttpStatus.NOT_IMPLEMENTED,
            "Recipes cannot be imported from that website"),
    IMPORT_FAILED(HttpStatus.BAD_GATEWAY, "The recipe could not be read from that website", true),

    SCAN_TOO_MANY_PAGES(HttpStatus.BAD_REQUEST,
            "A recipe may span fewer photographs than that"),
    SCAN_IMAGE_UNREADABLE(HttpStatus.BAD_REQUEST, "Nothing could be made of that photograph"),
    SCAN_IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "That photograph is too large"),
    SCAN_IMAGE_UNSUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "That file format cannot be read"),
    SCAN_NO_TEXT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT,
            "No readable text was found on the photographs"),
    SCAN_DAILY_LIMIT_REACHED(HttpStatus.TOO_MANY_REQUESTS,
            "The daily allowance of scans is used up", true),
    SCAN_BUSY(HttpStatus.TOO_MANY_REQUESTS, "Recipe scanning is busy right now", true),
    SCAN_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Recipe scanning is unavailable", true),
    SCAN_TIMED_OUT(HttpStatus.GATEWAY_TIMEOUT, "Reading the photographs took too long", true),
    SCAN_FAILED(HttpStatus.BAD_GATEWAY, "The photographs could not be read"),

    TEMPORARILY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "The server cannot answer that right now", true),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on the server", true);

    private final HttpStatus status;
    private final String message;
    /** Whether sending the very same request again could succeed. */
    private final boolean retryable;

    ApiErrorCode(HttpStatus status, String message) {
        this(status, message, false);
    }

    ApiErrorCode(HttpStatus status, String message, boolean retryable) {
        this.status = status;
        this.message = message;
        this.retryable = retryable;
    }

    /**
     * What to call a failure that is only known by its status - one the framework raised, or one
     * a library threw. Keeps a status this server never names from being answered as a mystery.
     *
     * @param status the status the failure carries
     * @return the closest code, never null
     */
    public static ApiErrorCode ofStatus(HttpStatus status) {
        if (status == null) {
            return INTERNAL_ERROR;
        }
        return switch (status) {
            case UNAUTHORIZED -> AUTHENTICATION_REQUIRED;
            case FORBIDDEN -> ACCESS_DENIED;
            case NOT_FOUND -> RESOURCE_NOT_FOUND;
            case METHOD_NOT_ALLOWED -> METHOD_NOT_ALLOWED;
            case NOT_ACCEPTABLE -> NOT_ACCEPTABLE;
            case CONFLICT -> CONFLICT;
            case CONTENT_TOO_LARGE -> FILE_TOO_LARGE;
            case UNSUPPORTED_MEDIA_TYPE -> UNSUPPORTED_MEDIA_TYPE;
            case TOO_MANY_REQUESTS -> RATE_LIMITED;
            case SERVICE_UNAVAILABLE -> TEMPORARILY_UNAVAILABLE;
            default -> status.is4xxClientError() ? MALFORMED_REQUEST : INTERNAL_ERROR;
        };
    }
}
