package com.sterul.opencookbookapiserver.integration;

import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.sterul.opencookbookapiserver.entities.PlanScope;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.entities.shopping.ItemChange;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingItem;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingList;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingListEvent;
import com.sterul.opencookbookapiserver.entities.shopping.Rhythm;
import com.sterul.opencookbookapiserver.entities.shopping.ShoppingRegular;
import com.sterul.opencookbookapiserver.repositories.ShoppingItemRepository;
import com.sterul.opencookbookapiserver.repositories.ShoppingListEventRepository;
import com.sterul.opencookbookapiserver.repositories.ShoppingListRepository;
import com.sterul.opencookbookapiserver.repositories.ShoppingRegularRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.shopping.ShoppingListService;
import com.sterul.opencookbookapiserver.services.shopping.ShoppingNames;
import com.sterul.opencookbookapiserver.services.shopping.history.ShoppingHistory;
import com.sterul.opencookbookapiserver.services.shopping.history.ShoppingRegulars;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingOp;
import com.sterul.opencookbookapiserver.services.shopping.sync.ShoppingSyncService;
import com.sterul.opencookbookapiserver.unit.MovableClock;

/** What a list remembers of people's changes, what it learns from them, and how it suggests what is due again. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class ShoppingSuggestionsIntegrationTest extends IntegrationTestBase {

    private static final String COOK = "suggest-cook@example.invalid";
    private static final String STRANGER = "suggest-stranger@example.invalid";
    private static final Instant START = Instant.parse("2026-09-05T10:00:00Z");
    private static final Duration A_WHILE = Duration.ofMinutes(10);
    private static final Duration A_MOMENT = Duration.ofSeconds(30);

    @TestConfiguration
    static class Time {

        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock(START);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MovableClock clock;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ShoppingListRepository listRepository;
    @Autowired
    private ShoppingItemRepository itemRepository;
    @Autowired
    private ShoppingListEventRepository eventRepository;
    @Autowired
    private ShoppingRegularRepository regularRepository;
    @Autowired
    private ShoppingListService lists;
    @Autowired
    private ShoppingSyncService sync;
    @Autowired
    private ShoppingHistory history;
    @Autowired
    private ShoppingRegulars regulars;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private CookpalUser cook;
    private PlanScope scope;
    private Long listId;

    @BeforeEach
    void setup() {
        clock.moveTo(START);
        listRepository.deleteAll();
        cook = TestAccounts.ensure(userRepository, COOK);
        TestAccounts.ensure(userRepository, STRANGER);
        scope = PlanScope.of(cook);
        listId = lists.defaultListIn(scope).getId();
    }

    @Test
    void aNameTakenOffWithinTwoMinutesLeavesNoTrace() {
        add("Milch");
        clock.advanceBy(Duration.ofMinutes(1));
        apply(new ShoppingOp.Buy(opId(), item("Milch").getId(), clock.instant()));
        add("Brot");
        clock.advanceBy(Duration.ofMinutes(1));
        apply(new ShoppingOp.Delete(opId(), item("Brot").getId(), clock.instant()));

        assertEquals(0, eventRepository.count());
    }

    @Test
    void aNameRenamedAndTakenOffWithinTwoMinutesLeavesNoTraceUnderEitherName() {
        add("Mlich");
        clock.advanceBy(A_MOMENT);
        var id = item("Mlich").getId();
        apply(new ShoppingOp.Update(opId(), id, clock.instant(), "Milch", null, null, null));
        clock.advanceBy(A_MOMENT);
        apply(new ShoppingOp.Delete(opId(), id, clock.instant()));

        assertEquals(0, eventRepository.count());
    }

    @Test
    void whoChangedWhatIsRecordedWhenItHappens() {
        add("Milch");
        add("Brot");
        clock.advanceBy(A_WHILE);
        apply(new ShoppingOp.Buy(opId(), item("Milch").getId(), clock.instant()));
        apply(new ShoppingOp.Delete(opId(), item("Brot").getId(), clock.instant()));

        assertEquals(List.of("ADDED Milch " + START, "ADDED Brot " + START, "BOUGHT Milch " + START.plus(A_WHILE),
                "REMOVED Brot " + START.plus(A_WHILE)), events());
        assertEquals(4, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM shopping_list_event WHERE actor_user_id = ?", Long.class, cook.getUserId()));
    }

    @Test
    void askingForMoreIsAnAdditionButNoFreshStay() {
        add("Milch");
        clock.advanceBy(A_WHILE);
        add("Milch");
        clock.advanceBy(A_MOMENT);
        apply(new ShoppingOp.Buy(opId(), item("Milch").getId(), clock.instant()));

        assertEquals(List.of("ADDED Milch " + START, "ADDED Milch " + START.plus(A_WHILE),
                "BOUGHT Milch " + START.plus(A_WHILE).plus(A_MOMENT)), events());
    }

    @Test
    void eachFieldAnEditChangedIsRecorded() {
        add("Käse");
        var id = item("Käse").getId();
        apply(new ShoppingOp.Update(opId(), id, clock.instant(), "Gouda", "200 g", Aisle.DELI, true));
        apply(new ShoppingOp.Update(opId(), id, clock.instant(), null, "200 g", null, false));

        var edits = eventRepository.findAll().stream().filter(event -> event.getKind() != ItemChange.ADDED)
                .map(event -> event.getKind() + " " + event.getName() + " " + event.getSpec() + " "
                        + event.getPreviousName())
                .sorted().toList();
        assertEquals(List.of("AISLE_CHANGED Gouda null null", "PRIORITIZED Gouda null null",
                "RENAMED Gouda null Käse", "SPEC_CHANGED Gouda 200 g null", "UNPRIORITIZED Gouda null null"), edits);
    }

    @Test
    void aTickTakenBackRightAwayWasNoPurchase() {
        add("Milch");
        clock.advanceBy(A_WHILE);
        apply(new ShoppingOp.Buy(opId(), item("Milch").getId(), clock.instant()));
        clock.advanceBy(A_MOMENT);
        apply(new ShoppingOp.Restore(opId(), item("Milch").getId(), clock.instant(), null));

        assertEquals(List.of("ADDED Milch " + START), events());
        assertEquals(START, item("Milch").getListedAt());
    }

    @Test
    void anOfflineTickDatedBeforeAnotherDeviceListedTheNameIsAPurchase() {
        add("Milch");
        clock.advanceBy(A_MOMENT);
        var offlineTick = START.minus(Duration.ofMinutes(30));
        apply(new ShoppingOp.Buy(opId(), item("Milch").getId(), offlineTick));

        assertEquals(List.of("BOUGHT Milch " + offlineTick, "ADDED Milch " + START), events());
    }

    @Test
    void anOfflineChangeIsDatedByTheDeviceButNeverInTheFuture() {
        add("Milch");
        add("Brot");
        clock.advanceBy(Duration.ofHours(2));
        apply(new ShoppingOp.Buy(opId(), item("Milch").getId(), START.plus(Duration.ofMinutes(90))));
        apply(new ShoppingOp.Buy(opId(), item("Brot").getId(), clock.instant().plus(Duration.ofDays(1))));

        assertEquals(START.plus(Duration.ofMinutes(90)), bought("Milch").getAt());
        assertEquals(clock.instant(), bought("Brot").getAt());
    }

    @Test
    void threeWeeklyPurchasesAreSuggestedADayAheadAndAgainAWeekLaterUntilTheListStopsBuying() {
        boughtWeekly("Milch", 3);
        var lastBought = clock.instant();

        assertEquals(Duration.ofDays(7).toSeconds(), regular("Milch").getRhythmSeconds());
        assertEquals(List.of(
                new Rhythm.Window(lastBought.plus(Duration.ofDays(6)), lastBought.plus(Duration.ofDays(6).plusHours(84))),
                new Rhythm.Window(lastBought.plus(Duration.ofDays(13)), lastBought.plus(Duration.ofDays(13).plusHours(84))),
                new Rhythm.Window(lastBought.plus(Duration.ofDays(20)), lastBought.plus(Duration.ofDays(21)))),
                regular("Milch").windows());
    }

    @Test
    void anAddedSuggestionIsAnAdditionAndItsPurchaseMovesTheRhythmOn() {
        boughtWeekly("Milch", 3);
        clock.advanceBy(Duration.ofDays(6));

        add("Milch");
        clock.advanceBy(A_WHILE);
        apply(new ShoppingOp.Buy(opId(), item("Milch").getId(), clock.instant()));

        assertEquals(4, eventRepository.findAll().stream().filter(event -> event.getKind() == ItemChange.BOUGHT).count());
        assertEquals(clock.instant(), regular("Milch").getLastBoughtAt());
        assertEquals(clock.instant().plus(Duration.ofDays(6)), regular("Milch").windows().getFirst().from());
    }

    @Test
    void notNowShowsTheSuggestionHalfARhythmLater() throws Exception {
        boughtWeekly("Milch", 3);
        clock.advanceBy(Duration.ofDays(6));

        mockMvc.perform(post("/api/v1/shopping/lists/" + listId + "/regulars/" + regular("Milch").getId() + "/not-now")
                        .with(user(COOK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windows[0].from").value(clock.instant().plus(Duration.ofHours(84)).toString()));
        mockMvc.perform(post("/api/v1/shopping/lists/" + listId + "/regulars/" + regular("Milch").getId() + "/not-now")
                        .with(user(STRANGER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void notSuggestingANameHoldsUntilSomebodyResumesIt() throws Exception {
        boughtWeekly("Milch", 3);
        var regularPath = "/api/v1/shopping/lists/" + listId + "/regulars/" + regular("Milch").getId();

        mockMvc.perform(put(regularPath).with(user(COOK)).contentType(MediaType.APPLICATION_JSON).content("{\"muted\":true}"))
                .andExpect(jsonPath("$.muted").value(true))
                .andExpect(jsonPath("$.windows.length()").value(0));
        clock.advanceBy(Duration.ofDays(7).minus(A_WHILE));
        boughtWeekly("Milch", 1);
        assertTrue(regular("Milch").windows().isEmpty());
        mockMvc.perform(put(regularPath).with(user(COOK)).contentType(MediaType.APPLICATION_JSON).content("{\"muted\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muted").value(false))
                .andExpect(jsonPath("$.everyDays").value(7))
                .andExpect(jsonPath("$.windows.length()").value(3));
    }

    @Test
    void aListsEntriesAndRegularsAreForThoseWhoMayUseIt() throws Exception {
        boughtWeekly("Milch", 3);
        add("Hafermilch Barista");
        clock.advanceBy(A_WHILE);
        apply(new ShoppingOp.Delete(opId(), item("Hafermilch Barista").getId(), clock.instant()));

        mockMvc.perform(get("/api/v1/shopping/lists/" + listId + "/entries").with(user(COOK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Hafermilch Barista"))
                .andExpect(jsonPath("$[1].name").value("Milch"))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/v1/shopping/lists/" + listId + "/regulars").with(user(COOK)))
                .andExpect(jsonPath("$[0].name").value("Milch"))
                .andExpect(jsonPath("$[0].everyDays").value(7))
                .andExpect(jsonPath("$[0].windows.length()").value(3))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/api/v1/shopping/lists/" + listId + "/entries").with(user(STRANGER)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/shopping/lists/" + listId + "/regulars").with(user(STRANGER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theHistoryShowsTheNewestFirstAPageAtATime() throws Exception {
        for (var index = 0; index < 52; index++) {
            add("Ding " + index);
            clock.advanceBy(Duration.ofSeconds(1));
        }

        mockMvc.perform(get("/api/v1/shopping/lists/" + listId + "/history").with(user(COOK)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(50))
                .andExpect(jsonPath("$.events[0].name").value("Ding 51"))
                .andExpect(jsonPath("$.events[0].kind").value("ADDED"))
                .andExpect(jsonPath("$.events[0].byViewer").value(true))
                .andExpect(jsonPath("$.events[0].actor").value("s…@example.invalid"))
                .andExpect(jsonPath("$.last").value(false));
        mockMvc.perform(get("/api/v1/shopping/lists/" + listId + "/history").param("page", "1").with(user(COOK)))
                .andExpect(jsonPath("$.events[*].name", contains("Ding 1", "Ding 0")))
                .andExpect(jsonPath("$.last").value(true));
        mockMvc.perform(get("/api/v1/shopping/lists/" + listId + "/history").with(user(STRANGER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void entriesNotUsedForHalfAYearAreNoLongerOffered() throws Exception {
        add("Glühwein");
        clock.advanceBy(A_WHILE);
        apply(new ShoppingOp.Buy(opId(), item("Glühwein").getId(), clock.instant()));
        clock.advanceBy(Duration.ofDays(184));

        mockMvc.perform(get("/api/v1/shopping/lists/" + listId + "/entries").with(user(COOK)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aYearLaterTheHistoryIsGoneAndOnlyMutedNamesAreRemembered() {
        boughtWeekly("Milch", 3);
        boughtWeekly("Brot", 3);
        regulars.mute(listId, scope, regular("Brot").getId(), true);
        clock.advanceBy(Duration.ofDays(366));

        history.forgetOld();
        regulars.forgetOld();

        assertEquals(0, eventRepository.count());
        assertEquals(List.of("brot"), regularRepository.findAll().stream().map(ShoppingRegular::getNameKey).toList());
    }

    @Test
    void aShoppingSettingLeftOutStaysAsItIs() throws Exception {
        var settings = "/api/v1/users/self/shoppingSettings";
        mockMvc.perform(put(settings).with(user(COOK)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"COOKPAL\",\"suggestions\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(settings).with(user(COOK)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"suggestions\":false}"))
                .andExpect(jsonPath("$.shoppingProvider").value("COOKPAL"))
                .andExpect(jsonPath("$.shoppingSuggestions").value(false));
        mockMvc.perform(put(settings).with(user(COOK)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"BRING\"}"))
                .andExpect(jsonPath("$.shoppingProvider").value("BRING"))
                .andExpect(jsonPath("$.shoppingSuggestions").value(false));
    }

    private void boughtWeekly(String name, int times) {
        for (var time = 0; time < times; time++) {
            if (time > 0) {
                clock.advanceBy(Duration.ofDays(7).minus(A_WHILE));
            }
            add(name);
            clock.advanceBy(A_WHILE);
            apply(new ShoppingOp.Buy(opId(), item(name).getId(), clock.instant()));
        }
    }

    private void add(String name) {
        apply(new ShoppingOp.Add(opId(), UUID.randomUUID().toString(), clock.instant(), name, null, Aisle.DAIRY_EGGS,
                null, List.of(), false));
    }

    private void apply(ShoppingOp op) {
        sync.apply(listId, scope, List.of(op), 0, cook);
    }

    private ShoppingItem item(String name) {
        return itemRepository.findByListAndNameKeyAndDeletedFalse(list(), ShoppingNames.key(name)).orElseThrow();
    }

    private ShoppingRegular regular(String name) {
        return regularRepository.findByListAndNameKey(list(), ShoppingNames.key(name)).orElseThrow();
    }

    private ShoppingListEvent bought(String name) {
        return eventRepository.findAll().stream()
                .filter(event -> event.getName().equals(name) && event.getKind() == ItemChange.BOUGHT)
                .findFirst().orElseThrow();
    }

    /** "KIND Name at", oldest first. */
    private List<String> events() {
        return eventRepository.findAll().stream()
                .sorted(Comparator.comparing(ShoppingListEvent::getAt).thenComparing(ShoppingListEvent::getId))
                .map(event -> event.getKind() + " " + event.getName() + " " + event.getAt()).toList();
    }

    private ShoppingList list() {
        return listRepository.findById(listId).orElseThrow();
    }

    private static String opId() {
        return UUID.randomUUID().toString();
    }
}
