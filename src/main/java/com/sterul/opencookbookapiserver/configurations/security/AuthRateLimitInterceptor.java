package com.sterul.opencookbookapiserver.configurations.security;

import org.springframework.http.HttpHeaders;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.errors.ApiException;
import com.sterul.opencookbookapiserver.services.AuthRateLimiter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
public class AuthRateLimitInterceptor implements HandlerInterceptor {

    private final AuthRateLimiter rateLimiter;

    public AuthRateLimitInterceptor(AuthRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) throws ApiException {

        var clientAddress = request.getRemoteAddr();
        var decision = rateLimiter.recordAttempt(clientAddress);
        if (decision.allowed()) {
            return true;
        }

        log.info("Refusing {} from {}: too many attempts without a token",
                request.getRequestURI(), clientAddress);
        response.setHeader(HttpHeaders.RETRY_AFTER,
                Long.toString(Math.max(1, decision.retryAfter().toSeconds())));
        throw new ApiException(ApiErrorCode.RATE_LIMITED, "Auth rate limit exceeded");
    }
}
