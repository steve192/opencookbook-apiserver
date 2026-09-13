package com.sterul.opencookbookapiserver.configurations.sharing;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ShareAccessRateLimitInterceptor implements HandlerInterceptor {

    static final String SHARE_ID_VARIABLE = "shareId";

    private final ShareAccessCheck accessCheck;

    public ShareAccessRateLimitInterceptor(ShareAccessCheck accessCheck) {
        this.accessCheck = accessCheck;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) throws ApiException {
        var shareId = shareIdOf(request);
        if (shareId == null) {
            // Nothing was matched, so there is no share to charge and the request is about to be
            // answered with a 404 without touching anything worth protecting.
            return true;
        }

        var decision = accessCheck.check(request.getRemoteAddr(), shareId);
        if (decision.allowed()) {
            return true;
        }

        log.info("Refusing access to share {} from {}: rate limit exceeded", shareId,
                request.getRemoteAddr());
        // Set before throwing, on the response the error body is about to be written to. An
        // interceptor runs inside the dispatcher servlet, so throwing from here reaches the one
        // handler that formats errors instead of writing a second kind of body by hand.
        response.setHeader(HttpHeaders.RETRY_AFTER,
                Long.toString(Math.max(1, decision.retryAfter().toSeconds())));
        throw new ApiException(ApiErrorCode.RATE_LIMITED, "Share access rate limit exceeded");
    }

    private String shareIdOf(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        var pathVariables = (Map<String, String>) request
                .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return pathVariables == null ? null : pathVariables.get(SHARE_ID_VARIABLE);
    }
}
