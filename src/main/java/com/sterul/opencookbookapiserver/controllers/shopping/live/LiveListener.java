package com.sterul.opencookbookapiserver.controllers.shopping.live;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;

/** One open app: who it is, until when its token holds, and which lists it shows. */
final class LiveListener {

    private static final int SEND_TIME_LIMIT_MILLIS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 64 * 1024;

    private final WebSocketSession session;
    private final CookpalUser user;
    private final Instant tokenExpiresAt;
    private final Set<ClientMessage.ListReference> subscriptions = ConcurrentHashMap.newKeySet();

    LiveListener(WebSocketSession session, CookpalUser user, Instant tokenExpiresAt) {
        // Hints are sent from whichever thread committed a change; the decorator serialises them.
        this.session = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MILLIS, BUFFER_SIZE_LIMIT);
        this.user = user;
        this.tokenExpiresAt = tokenExpiresAt;
    }

    WebSocketSession session() {
        return session;
    }

    CookpalUser user() {
        return user;
    }

    Instant tokenExpiresAt() {
        return tokenExpiresAt;
    }

    Set<ClientMessage.ListReference> subscriptions() {
        return subscriptions;
    }

    boolean listensTo(Long listId) {
        return subscriptions.stream().anyMatch(subscription -> subscription.listId().equals(listId));
    }
}
