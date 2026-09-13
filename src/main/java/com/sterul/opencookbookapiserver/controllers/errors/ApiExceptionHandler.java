package com.sterul.opencookbookapiserver.controllers.errors;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.WebUtils;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns every failure into the one error body clients understand.
 *
 * Extends Spring's handler rather than a bare advice: our {@code @ExceptionHandler(Exception)}
 * would otherwise out-match the framework's own mappings, answering 500 where it answers 400.
 * The framework keeps deciding the status; only the body is ours. Anything unmapped is
 * {@link ApiErrorCode#INTERNAL_ERROR} and only the log learns why.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException e) {
        var status = e.getErrorCode().getStatus();
        logAtSeverityOf(status, e);
        return ResponseEntity.status(status).body(ApiErrorResponse.of(e.getErrorCode()));
    }

    /**
     * Authentication that could not be carried out, rather than one that failed. Something
     * behind the login is broken - the database, most likely - and reporting that as a wrong
     * password would send everybody to reset a password that was never the problem.
     */
    @ExceptionHandler(InternalAuthenticationServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthenticationBroken(
            InternalAuthenticationServiceException e) {
        log.error("Could not authenticate a request", e);
        return respond(ApiErrorCode.INTERNAL_ERROR);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException e) {
        log.debug("Rejecting an unauthenticated request", e);
        return respond(ApiErrorCode.AUTHENTICATION_REQUIRED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException e) {
        log.debug("Refusing a request the caller may not make", e);
        return respond(ApiErrorCode.ACCESS_DENIED);
    }

    /** Sending mail is somebody else's server, so a failure is theirs and worth retrying. */
    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<ApiErrorResponse> handleMailFailure(MessagingException e) {
        log.error("Could not send an e-mail", e);
        return respond(ApiErrorCode.MAIL_DELIVERY_FAILED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception while answering a request", e);
        return respond(ApiErrorCode.INTERNAL_ERROR);
    }

    /** Overridden to keep the rejected fields, which the shared body alone does not carry. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {

        var fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiErrorResponse.FieldError(
                        error.getField(), error.getDefaultMessage()))
                .toList();
        return handleExceptionInternal(ex,
                ApiErrorResponse.ofFields(ApiErrorCode.VALIDATION_FAILED, fieldErrors),
                headers, status, request);
    }

    /** A rejected parameter is a validation failure too, not an unreadable request. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {

        return handleExceptionInternal(ex, ApiErrorResponse.of(ApiErrorCode.VALIDATION_FAILED),
                headers, status, request);
    }

    /**
     * The single place the framework's own failures are given our shape. Spring has already
     * decided the status; a body it prepared is replaced, because that one carries the detail
     * we do not publish.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        if (request instanceof ServletWebRequest servletRequest
                && servletRequest.getResponse() != null
                && servletRequest.getResponse().isCommitted()) {
            log.warn("Response already committed, cannot report: {}", ex.toString());
            return null;
        }

        var status = HttpStatus.resolve(statusCode.value());
        logAtSeverityOf(status, ex);

        // Lets the container's own error handling see what happened, as Spring's version does.
        if (statusCode.is5xxServerError()) {
            request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, ex, WebRequest.SCOPE_REQUEST);
        }

        var ours = body instanceof ApiErrorResponse prepared ?
                prepared :
                ApiErrorResponse.of(ApiErrorCode.ofStatus(status), statusCode.value());

        return ResponseEntity.status(statusCode).headers(headers).body(ours);
    }

    private static ResponseEntity<ApiErrorResponse> respond(ApiErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(ApiErrorResponse.of(code));
    }

    /** A caller's mistake is not worth a stack trace; ours always is. */
    private static void logAtSeverityOf(HttpStatus status, Exception e) {
        if (status != null && status.is5xxServerError()) {
            log.error("Answering {} to a request", status, e);
        } else {
            log.debug("Answering {} to a request", status, e);
        }
    }
}
