package com.sterul.opencookbookapiserver.controllers.errors;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.sterul.opencookbookapiserver.errors.ApiErrorCode;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the error body for a failure raised outside the dispatcher servlet. Only the security
 * filter chain needs this, because it answers before {@link ApiExceptionHandler} can be reached.
 * Anything inside the dispatcher, interceptors included, throws instead.
 */
@Component
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ApiErrorCode code) throws IOException {
        var body = ApiErrorResponse.of(code);
        response.setStatus(body.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
