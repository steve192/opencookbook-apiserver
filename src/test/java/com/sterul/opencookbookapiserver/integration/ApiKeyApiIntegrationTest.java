package com.sterul.opencookbookapiserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.repositories.ApiKeyRepository;
import com.sterul.opencookbookapiserver.repositories.ShoppingListRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;

/** A key reaches exactly what its scopes name, and nothing a key was never meant for. */
@SpringBootTest(properties = "opencookbook.api-keys.max-per-user=3")
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class ApiKeyApiIntegrationTest extends IntegrationTestBase {

    private static final String ANNA = "keys-anna@example.invalid";
    private static final String BERT = "keys-bert@example.invalid";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ApiKeyRepository keyRepository;
    @Autowired
    private ShoppingListRepository listRepository;

    @BeforeEach
    void setup() {
        keyRepository.deleteAll();
        listRepository.deleteAll();
        List.of(ANNA, BERT).forEach(name -> TestAccounts.ensure(userRepository, name));
        var anna = userRepository.findByEmailAddress(ANNA);
        anna.setActivated(true);
        userRepository.save(anna);
    }

    @Test
    void theSecretIsShownOnceAndAWriteScopeBringsItsReadScope() throws Exception {
        mockMvc.perform(post("/api/v1/api-keys").with(user(ANNA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Home Assistant \",\"scopes\":[\"shopping:write\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secret").value(startsWith("cpk_")))
                .andExpect(jsonPath("$.key.name").value("Home Assistant"))
                .andExpect(jsonPath("$.key.scopes").value(contains("shopping:read", "shopping:write")));

        mockMvc.perform(get("/api/v1/api-keys").with(user(ANNA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].displayPrefix").value(startsWith("cpk_")))
                .andExpect(jsonPath("$[0].secret").doesNotExist());
        assertThat(keyRepository.findAll().get(0).getSecretHash()).doesNotStartWith("cpk_");
    }

    @Test
    void anUnknownScopeOrNoScopeIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/api-keys").with(user(ANNA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"scopes\":[\"recipes:write\"]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/api-keys").with(user(ANNA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"scopes\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aKeyReadsAndWritesTheShoppingList() throws Exception {
        var key = createKey(ANNA, "shopping:write");
        var list = firstList(key);

        mockMvc.perform(post("/api/v1/shopping/lists/" + list + "/ops").with(TestAccounts.bearer(key))
                        .contentType(MediaType.APPLICATION_JSON).content(addOp("Milch")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/shopping/lists/" + list + "/changes").with(TestAccounts.bearer(key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Milch"));
        mockMvc.perform(get("/api/v1/shopping/vocabulary").with(TestAccounts.bearer(key)))
                .andExpect(status().isOk());
    }

    @Test
    void aReadKeyCannotWrite() throws Exception {
        var key = createKey(ANNA, "shopping:read");
        var list = firstList(key);

        mockMvc.perform(post("/api/v1/shopping/lists/" + list + "/ops").with(TestAccounts.bearer(key))
                        .contentType(MediaType.APPLICATION_JSON).content(addOp("Milch")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void aKeyReachesNothingThatDoesNotLetKeysIn() throws Exception {
        var key = createKey(ANNA, "shopping:write");

        mockMvc.perform(get("/api/v1/recipes").with(TestAccounts.bearer(key)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/users/self").with(TestAccounts.bearer(key)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/api-keys").with(TestAccounts.bearer(key)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/api-keys").with(TestAccounts.bearer(key))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"more\",\"scopes\":[\"shopping:read\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/shopping/lists").with(TestAccounts.bearer(key))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Baumarkt\"}"))
                .andExpect(status().isForbidden());
        assertThat(keyRepository.count()).isEqualTo(1);
    }

    @Test
    void aKeyLearnsAboutItself() throws Exception {
        var key = createKey(ANNA, "shopping:read");

        mockMvc.perform(get("/api/v1/api-keys/current").with(TestAccounts.bearer(key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Home Assistant"))
                .andExpect(jsonPath("$.scopes").value(contains("shopping:read")))
                .andExpect(jsonPath("$.accountId").value(userRepository.findByEmailAddress(ANNA).getUserId()))
                .andExpect(jsonPath("$.owner").value("k…@example.invalid"));
        assertThat(keyRepository.findAll().get(0).getLastUsedAt()).isNotNull();

        mockMvc.perform(get("/api/v1/api-keys/current").with(user(ANNA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aRevokedOrUnknownKeySignsNobodyIn() throws Exception {
        var key = createKey(ANNA, "shopping:read");
        var id = keyRepository.findAll().get(0).getId();

        mockMvc.perform(delete("/api/v1/api-keys/" + id).with(user(BERT)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/api-keys/" + id).with(user(ANNA)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/shopping/lists").with(TestAccounts.bearer(key)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/v1/shopping/lists").with(TestAccounts.bearer("cpk_" + "a".repeat(43))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aDeactivatedAccountsKeysStopWorking() throws Exception {
        var key = createKey(ANNA, "shopping:read");
        var anna = userRepository.findByEmailAddress(ANNA);
        anna.setActivated(false);
        userRepository.save(anna);

        mockMvc.perform(get("/api/v1/shopping/lists").with(TestAccounts.bearer(key)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aKeyCannotOpenTheLiveSocket() throws Exception {
        var key = createKey(ANNA, "shopping:read");

        mockMvc.perform(get("/api/v1/shopping/live").with(TestAccounts.bearer(key))
                        .header(HttpHeaders.UPGRADE, "websocket"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdministratorsKeyIsNoAdministrator() throws Exception {
        var anna = userRepository.findByEmailAddress(ANNA);
        anna.setRoles(Role.ADMIN);
        userRepository.save(anna);
        try {
            var key = createKey(ANNA, "shopping:write");

            mockMvc.perform(get("/api/v1/admin/users").with(TestAccounts.bearer(key)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            mockMvc.perform(get("/api/v1/admin/users").with(TestAccounts.operator(ANNA)))
                    .andExpect(status().isOk());
        } finally {
            anna.setRoles(null);
            userRepository.save(anna);
        }
    }

    @Test
    void aKeyOnlyUsesTheMethodsItsScopeNames() throws Exception {
        var key = createKey(ANNA, "shopping:write");
        var list = firstList(key);

        mockMvc.perform(delete("/api/v1/shopping/lists/" + list).with(TestAccounts.bearer(key)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/shopping/lists/" + list + "/import").with(TestAccounts.bearer(key))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"lines\":[],\"shown\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAccountHoldsALimitedNumberOfKeys() throws Exception {
        for (var i = 0; i < 3; i++) {
            createKey(ANNA, "shopping:read");
        }

        mockMvc.perform(post("/api/v1/api-keys").with(user(ANNA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"one more\",\"scopes\":[\"shopping:read\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOO_MANY_API_KEYS"));
    }

    @Test
    void theInstanceSaysItTakesKeys() throws Exception {
        mockMvc.perform(get("/api/v1/instance"))
                .andExpect(jsonPath("$.apiKeysEnabled").value(true));
    }

    private String createKey(String who, String scope) throws Exception {
        var body = mockMvc.perform(post("/api/v1/api-keys").with(user(who)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Home Assistant\",\"scopes\":[\"" + scope + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.secret");
    }

    private long firstList(String key) throws Exception {
        var body = mockMvc.perform(get("/api/v1/shopping/lists").with(TestAccounts.bearer(key)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(body, "$[0].id").longValue();
    }

    private static String addOp(String name) {
        return "{\"ops\":[{\"opId\":\"" + UUID.randomUUID() + "\",\"type\":\"ADD\",\"itemId\":\"" + UUID.randomUUID()
                + "\",\"name\":\"" + name + "\"}]}";
    }
}
