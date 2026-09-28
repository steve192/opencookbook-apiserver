package com.sterul.opencookbookapiserver.controllers.shopping.live;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.configurations.security.requestfilters.BearerTokens;
import com.sterul.opencookbookapiserver.configurations.shopping.ConditionalOnShoppingLive;
import com.sterul.opencookbookapiserver.controllers.support.PlanScopes;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.services.UserService;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;
import com.sterul.opencookbookapiserver.services.shopping.ShoppingListService;
import com.sterul.opencookbookapiserver.util.JwtTokenUtil;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Where open apps listen for list changes. The upgrade is authenticated like any other request, so
 * every socket belongs to somebody; a browser offers its token as a subprotocol (see BearerTokens),
 * and the server answers with {@link #PROTOCOL}, never the token.
 */
@Component
@ConditionalOnShoppingLive
public class ShoppingLiveHandler extends TextWebSocketHandler implements SubProtocolCapable {

    public static final String PROTOCOL = "cookpal-live";

    /** An app only ever sends the lists it shows. */
    private static final int MAX_MESSAGE_BYTES = 4096;

    private final LiveListeners listeners;
    private final JwtTokenUtil tokens;
    private final UserService users;
    private final PlanScopes planScopes;
    private final ShoppingListService lists;
    private final OpencookbookConfiguration configuration;
    private final JsonMapper json;

    public ShoppingLiveHandler(LiveListeners listeners, JwtTokenUtil tokens, UserService users, PlanScopes planScopes,
            ShoppingListService lists, OpencookbookConfiguration configuration, JsonMapper json) {
        this.listeners = listeners;
        this.tokens = tokens;
        this.users = users;
        this.planScopes = planScopes;
        this.lists = lists;
        this.configuration = configuration;
        this.json = json;
    }

    @Override
    public List<String> getSubProtocols() {
        return List.of(PROTOCOL);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        session.setTextMessageSizeLimit(MAX_MESSAGE_BYTES);
        var user = users.getUserByEmail(session.getPrincipal().getName());
        if (listeners.devicesOf(user.getUserId()) >= configuration.getShopping().getLiveSocketsPerUser()) {
            session.close(LiveListeners.TOO_MANY_DEVICES);
            return;
        }
        var token = BearerTokens.of(session.getHandshakeHeaders()).orElseThrow();
        listeners.opened(session, user, tokens.getExpirationDateFromToken(token).toInstant());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        listeners.closed(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage text) {
        var listener = listeners.of(session);
        try {
            var message = json.readValue(text.getPayload(), ClientMessage.class);
            if (ClientMessage.SUBSCRIBE.equals(message.type())) {
                subscribe(listener, message.lists() == null ? List.of() : message.lists());
                return;
            }
        } catch (JacksonException unreadable) {
            // Closed below, like a message of an unknown type.
        }
        listeners.close(listener, CloseStatus.BAD_DATA);
    }

    /** Replaces what the app listened to; a list it may not use is left out rather than refused. */
    private void subscribe(LiveListener listener, List<ClientMessage.ListReference> wanted) {
        var granted = wanted.stream()
                .filter(reference -> reference.listId() != null && mayUse(listener.user(), reference))
                .toList();
        listener.subscriptions().clear();
        listener.subscriptions().addAll(granted);
        listeners.send(listener, new ServerMessage.Subscribed(
                granted.stream().map(ClientMessage.ListReference::listId).toList()));
    }

    private boolean mayUse(CookpalUser user, ClientMessage.ListReference reference) {
        try {
            lists.listIn(reference.listId(), planScopes.of(user, reference.householdId()));
            return true;
        } catch (ElementNotFound notTheirs) {
            return false;
        }
    }
}
