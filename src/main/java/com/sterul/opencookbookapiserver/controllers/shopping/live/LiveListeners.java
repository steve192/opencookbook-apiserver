package com.sterul.opencookbookapiserver.controllers.shopping.live;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import com.sterul.opencookbookapiserver.configurations.shopping.ConditionalOnShoppingLive;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.services.households.HouseholdEnding;
import com.sterul.opencookbookapiserver.services.households.HouseholdMembershipService;
import com.sterul.opencookbookapiserver.services.households.ReadAccessNarrowed;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingListChanged;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every open app on this server and the lists it shows. In memory: a hint lost to a restart, or to a
 * second server, only means the app notices the change when it next syncs.
 */
@Component
@ConditionalOnShoppingLive
@Slf4j
public class LiveListeners {

    /** The token ran out: the app renews it and connects again. */
    static final CloseStatus AUTHENTICATE = new CloseStatus(4001, "authenticate");
    static final CloseStatus TOO_MANY_DEVICES = new CloseStatus(4008, "too many devices");

    private final Map<String, LiveListener> bySession = new ConcurrentHashMap<>();
    private final HouseholdMembershipService memberships;
    private final Clock clock;
    private final JsonMapper json;

    public LiveListeners(HouseholdMembershipService memberships, Clock clock, JsonMapper json) {
        this.memberships = memberships;
        this.clock = clock;
        this.json = json;
    }

    void opened(WebSocketSession session, CookpalUser user, Instant tokenExpiresAt) {
        bySession.put(session.getId(), new LiveListener(session, user, tokenExpiresAt));
    }

    void closed(WebSocketSession session) {
        bySession.remove(session.getId());
    }

    LiveListener of(WebSocketSession session) {
        return bySession.get(session.getId());
    }

    long devicesOf(Long userId) {
        return bySession.values().stream()
                .filter(listener -> listener.user().getUserId().equals(userId))
                .count();
    }

    void send(LiveListener listener, ServerMessage message) {
        send(listener, new TextMessage(json.writeValueAsString(message)));
    }

    private void send(LiveListener listener, WebSocketMessage<?> message) {
        try {
            listener.session().sendMessage(message);
        } catch (IOException | IllegalStateException unreachable) {
            // Gone mid-send; closing tells the container, which calls back into closed().
            close(listener, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    void close(LiveListener listener, CloseStatus status) {
        try {
            listener.session().close(status);
        } catch (IOException alreadyGone) {
            bySession.remove(listener.session().getId());
        }
    }

    /** After the commit, so an app that pulls at once sees the change. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChanged(ShoppingListChanged changed) {
        var hint = new ServerMessage.Changed(changed.listId(), changed.version());
        bySession.values().stream().filter(listener -> listener.listensTo(changed.listId()))
                .forEach(listener -> send(listener, hint));
    }

    /** Somebody who left a household stops hearing about its lists. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNarrowed(ReadAccessNarrowed narrowed) {
        var members = memberships.memberIdsOf(narrowed.householdId());
        bySession.values().stream()
                .filter(listener -> !members.contains(listener.user().getUserId()))
                .forEach(listener -> dropHousehold(listener, narrowed.householdId()));
    }

    @EventListener
    public void onEnding(HouseholdEnding ending) {
        bySession.values().forEach(listener -> dropHousehold(listener, ending.householdId()));
    }

    /** Closes whoever's token ran out; pings the rest. */
    @Scheduled(fixedRate = 30_000)
    public void sweep() {
        var now = clock.instant();
        for (var listener : bySession.values()) {
            if (listener.tokenExpiresAt().isBefore(now)) {
                close(listener, AUTHENTICATE);
            } else {
                send(listener, new PingMessage());
            }
        }
    }

    private static void dropHousehold(LiveListener listener, String householdId) {
        listener.subscriptions().removeIf(subscription -> Objects.equals(subscription.householdId(), householdId));
    }
}
