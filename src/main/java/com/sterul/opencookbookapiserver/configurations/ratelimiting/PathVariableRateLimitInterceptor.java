package com.sterul.opencookbookapiserver.configurations.ratelimiting;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.ratelimiting.RateLimitDecision;
import com.sterul.opencookbookapiserver.ratelimiting.RateLimitedAccessCheck;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Spends a budget keyed by one path variable - the id or token the request is about - so that
 * walking ids costs more than reading one thing.
 */
@Slf4j
public class PathVariableRateLimitInterceptor implements HandlerInterceptor {

    private final String variableName;
    private final RateLimitedAccessCheck accessCheck;
    private final String subjectLabel;

    /** @param subjectLabel what the variable names, for the log line and the error message */
    public PathVariableRateLimitInterceptor(String variableName, RateLimitedAccessCheck accessCheck,
            String subjectLabel) {
        this.variableName = variableName;
        this.accessCheck = accessCheck;
        this.subjectLabel = subjectLabel;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var subject = subjectOf(request);
        if (subject == null) {
            // Nothing was matched, so there is nothing to charge and the request is about to be
            // answered with a 404 without touching anything worth protecting.
            return true;
        }

        var decision = accessCheck.check(request.getRemoteAddr(), subject);
        if (decision.allowed()) {
            return true;
        }

        // Never log the subject: it is the capability being spent, such as an invite token.
        log.info("Refusing {} from {}: rate limit exceeded", subjectLabel, request.getRemoteAddr());
        // Set before throwing, on the response the error body is about to be written to. An
        // interceptor runs inside the dispatcher servlet, so throwing from here reaches the one
        // handler that formats errors instead of writing a second kind of body by hand.
        response.setHeader(HttpHeaders.RETRY_AFTER, retryAfterSeconds(decision));
        throw new ApiException(ApiErrorCode.RATE_LIMITED, subjectLabel + " rate limit exceeded");
    }

    private static String retryAfterSeconds(RateLimitDecision decision) {
        return Long.toString(Math.max(1, decision.retryAfter().toSeconds()));
    }

    private String subjectOf(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        var pathVariables = (Map<String, String>) request
                .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return pathVariables == null ? null : pathVariables.get(variableName);
    }
}
