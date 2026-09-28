package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.configurations.security.requestfilters.BearerTokens;
import com.sterul.opencookbookapiserver.controllers.shopping.live.ShoppingLiveHandler;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.repositories.HouseholdRepository;
import com.sterul.opencookbookapiserver.repositories.ShoppingListRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.util.JwtTokenUtil;

/** An open app hears that a list changed, and only about lists it may use. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class ShoppingLiveIntegrationTest extends IntegrationTestBase {

    private static final String ANNA = "live-anna@example.invalid";
    private static final String BERT = "live-bert@example.invalid";
    private static final long WAIT_SECONDS = 5;

    @LocalServerPort
    private int port;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenUtil tokens;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ShoppingListRepository listRepository;
    @Autowired
    private HouseholdRepository householdRepository;
    @Autowired
    private HouseholdMembershipRepository membershipRepository;

    private final Listener listener = new Listener();
    private WebSocketSession socket;

    @BeforeEach
    void setup() throws Exception {
        listRepository.deleteAll();
        membershipRepository.deleteAll();
        householdRepository.deleteAll();
        TestAccounts.ensure(userRepository, ANNA);
        TestAccounts.ensure(userRepository, BERT);
    }

    @AfterEach
    void closeSocket() throws Exception {
        if (socket != null && socket.isOpen()) {
            socket.close();
        }
    }

    @Test
    void aChangeIsHintedToTheAppShowingTheList() throws Exception {
        var list = listOf(ANNA, null);
        connect(ANNA);
        subscribe("{\"listId\":" + list + "}");

        add(ANNA, list, null);

        var hint = listener.next();
        assertEquals("changed", JsonPath.read(hint, "$.type"));
        assertEquals(list, ((Number) JsonPath.read(hint, "$.listId")).longValue());
    }

    @Test
    void aListSomebodyMayNotUseIsLeftOut() throws Exception {
        var annas = listOf(ANNA, null);

        connect(BERT);
        var subscribed = subscribe("{\"listId\":" + annas + "}");

        assertEquals(List.of(), JsonPath.read(subscribed, "$.listIds"));
    }

    @Test
    void somebodyWhoLeftAHouseholdHearsNoMoreOfItsList() throws Exception {
        var household = HouseholdsForTests.start(mockMvc, ANNA, "Familie Test");
        HouseholdsForTests.join(mockMvc, household, ANNA, BERT);
        var shared = listOf(BERT, household);
        connect(BERT);
        subscribe("{\"listId\":" + shared + ",\"householdId\":\"" + household + "\"}");

        var bertsId = userRepository.findByEmailAddress(BERT).getUserId();
        mockMvc.perform(delete("/api/v1/households/" + household + "/members/" + bertsId).with(user(ANNA)))
                .andExpect(status().isNoContent());
        add(ANNA, shared, household);

        assertNull(listener.messages.poll(1, TimeUnit.SECONDS));
    }

    @Test
    void anUpgradeWithoutAValidTokenIsRefused() {
        assertThrows(ExecutionException.class, () -> open("not-a-token"));
    }

    @Test
    void theServerAnswersWithItsProtocolNeverTheToken() throws Exception {
        connect(ANNA);

        assertEquals(ShoppingLiveHandler.PROTOCOL, socket.getAcceptedProtocol());
    }

    /** As a browser does, which cannot set an Authorization header on a WebSocket. */
    private void connect(String who) throws Exception {
        socket = open(tokens.generateToken(User.withUsername(who).password("irrelevant").build()));
    }

    private WebSocketSession open(String token) throws Exception {
        var headers = new WebSocketHttpHeaders();
        headers.setSecWebSocketProtocol(List.of(ShoppingLiveHandler.PROTOCOL, BearerTokens.PROTOCOL_PREFIX + token));
        return new StandardWebSocketClient()
                .execute(listener, headers, URI.create("ws://localhost:" + port + "/api/v1/shopping/live"))
                .get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private String subscribe(String lists) throws Exception {
        socket.sendMessage(new TextMessage("{\"type\":\"subscribe\",\"lists\":[" + lists + "]}"));
        var answer = listener.next();
        assertTrue(answer.contains("subscribed"), answer);
        return answer;
    }

    private void add(String who, long list, String household) throws Exception {
        var op = "{\"ops\":[{\"opId\":\"%s\",\"type\":\"ADD\",\"itemId\":\"%s\",\"name\":\"Brot\"}]}"
                .formatted(UUID.randomUUID(), UUID.randomUUID());
        mockMvc.perform(post("/api/v1/shopping/lists/" + list + "/ops").param("household", household).with(user(who))
                        .contentType(MediaType.APPLICATION_JSON).content(op))
                .andExpect(status().isOk());
    }

    private long listOf(String who, String household) throws Exception {
        var body = mockMvc.perform(get("/api/v1/shopping/lists").with(user(who)))
                .andReturn().getResponse().getContentAsString();
        var path = household == null ? "$[?(@.householdId == null)].id" : "$[?(@.householdId == '" + household + "')].id";
        List<Number> ids = JsonPath.read(body, path);
        return ids.get(0).longValue();
    }

    private static final class Listener extends TextWebSocketHandler {

        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        String next() throws InterruptedException {
            var message = messages.poll(WAIT_SECONDS, TimeUnit.SECONDS);
            if (message == null) {
                throw new AssertionError("Nothing arrived within " + WAIT_SECONDS + " seconds");
            }
            return message;
        }
    }
}
