package com.sterul.opencookbookapiserver.configurations.sharing;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

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
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
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

        log.info("Refusing access to share {} from {}: rate limit exceeded", shareId, request.getRemoteAddr());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, decision.retryAfter().toSeconds())));
        return false;
    }

    private String shareIdOf(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        var pathVariables = (Map<String, String>) request
                .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return pathVariables == null ? null : pathVariables.get(SHARE_ID_VARIABLE);
    }
}
