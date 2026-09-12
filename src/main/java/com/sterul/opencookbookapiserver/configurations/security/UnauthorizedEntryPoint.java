package com.sterul.opencookbookapiserver.configurations.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;
import com.sterul.opencookbookapiserver.controllers.errors.ApiErrorWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Answers a request that never reached a controller for want of a valid token. */
@Component
public class UnauthorizedEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorWriter errorWriter;

    public UnauthorizedEntryPoint(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        errorWriter.write(response, ApiErrorCode.AUTHENTICATION_REQUIRED);
    }
}
