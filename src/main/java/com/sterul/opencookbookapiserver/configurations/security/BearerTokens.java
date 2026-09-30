package com.sterul.opencookbookapiserver.configurations.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.WebSocketHttpHeaders;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Where a request carries its access token. Browsers cannot set headers on a WebSocket, so an upgrade
 * offers it as the subprotocol "bearer.&lt;token&gt;" instead: out of URLs and access logs, and no page
 * can set that header through fetch.
 */
public final class BearerTokens {

    public static final String PROTOCOL_PREFIX = "bearer.";
    private static final String HEADER_PREFIX = "Bearer ";

    private BearerTokens() {
    }

    public static Optional<String> of(HttpServletRequest request) {
        return of(request.getHeader(HttpHeaders.AUTHORIZATION),
                Collections.list(request.getHeaders(WebSocketHttpHeaders.SEC_WEBSOCKET_PROTOCOL)));
    }

    /** @param protocols Sec-WebSocket-Protocol values, each a comma-separated list */
    private static Optional<String> of(String authorization, Collection<String> protocols) {
        if (authorization != null && authorization.startsWith(HEADER_PREFIX)) {
            return Optional.of(authorization.substring(HEADER_PREFIX.length()));
        }
        return protocols.stream()
                .flatMap(header -> Arrays.stream(header.split(",")))
                .map(String::strip)
                .filter(protocol -> protocol.startsWith(PROTOCOL_PREFIX))
                .map(protocol -> protocol.substring(PROTOCOL_PREFIX.length()))
                .findFirst();
    }
}
