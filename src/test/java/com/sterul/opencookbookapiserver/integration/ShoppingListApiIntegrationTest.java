package com.sterul.opencookbookapiserver.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.sterul.opencookbookapiserver.entities.catalogue.Aisle;
import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFood;
import com.sterul.opencookbookapiserver.entities.catalogue.CatalogueFoodName;
import com.sterul.opencookbookapiserver.entities.nutrition.NutrientValues;
import com.sterul.opencookbookapiserver.repositories.CatalogueFoodRepository;
import com.sterul.opencookbookapiserver.repositories.CatalogueNameRuleRepository;
import com.sterul.opencookbookapiserver.repositories.HouseholdMembershipRepository;
import com.sterul.opencookbookapiserver.repositories.HouseholdRepository;
import com.sterul.opencookbookapiserver.repositories.ShoppingListRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.catalogue.linking.NameRuleService;
import com.sterul.opencookbookapiserver.services.catalogue.matching.CatalogueIndexUpdater;

/** Lists, and how devices that were offline catch up with them without losing or doubling anything. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class ShoppingListApiIntegrationTest extends IntegrationTestBase {

    private static final String ANNA = "shop-anna@example.invalid";
    private static final String BERT = "shop-bert@example.invalid";
    private static final String STRANGER = "shop-stranger@example.invalid";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ShoppingListRepository listRepository;
    @Autowired
    private HouseholdRepository householdRepository;
    @Autowired
    private HouseholdMembershipRepository membershipRepository;
    @Autowired
    private CatalogueFoodRepository foodRepository;
    @Autowired
    private CatalogueIndexUpdater indexUpdater;
    @Autowired
    private CatalogueNameRuleRepository nameRuleRepository;
    @Autowired
    private NameRuleService nameRules;

    private String householdId;

    @BeforeEach
    void setup() throws Exception {
        listRepository.deleteAll();
        membershipRepository.deleteAll();
        householdRepository.deleteAll();
        nameRuleRepository.deleteAll();
        foodRepository.deleteAll();
        foodRepository.save(CatalogueFood.builder().catalogueKey("custom-flour").origin(CatalogueFood.Origin.CUSTOM)
                .nutrients(NutrientValues.builder().energyKcal(350f).build()).aisle(Aisle.BAKING)
                .names(new ArrayList<>(List.of(CatalogueFoodName.builder().languageIsoCode("de").name("Weizenmehl")
                        .display(true).origin(CatalogueFoodName.Origin.ADMIN).build())))
                .build());
        indexUpdater.rebuild();
        List.of(ANNA, BERT, STRANGER).forEach(name -> TestAccounts.ensure(userRepository, name));
        householdId = TestHouseholds.start(mockMvc, ANNA, "Familie Test");
        TestHouseholds.join(mockMvc, householdId, ANNA, BERT);
    }

    @Test
    void everyoneHasADefaultListAndSoDoesEachHousehold() throws Exception {
        mockMvc.perform(get("/api/v1/shopping/lists").with(user(BERT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].defaultList").value(true))
                .andExpect(jsonPath("$[0].householdId").doesNotExist())
                .andExpect(jsonPath("$[1].householdName").value("Familie Test"));
    }

    @Test
    void householdMembersShareTheirList() throws Exception {
        var list = householdList(ANNA);
        ops(ANNA, list, 0, add("Brot", "1")).andExpect(status().isOk());

        changes(BERT, list, 0)
                .andExpect(jsonPath("$.items[0].name").value("Brot"))
                .andExpect(jsonPath("$.items[0].addedBy").value("s…@example.invalid"));
    }

    @Test
    void somebodyElsesListIsNotFound() throws Exception {
        var annasOwn = ownList(ANNA);

        mockMvc.perform(get("/api/v1/shopping/lists/" + annasOwn + "/changes").with(user(BERT)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/shopping/lists/" + householdList(ANNA) + "/changes")
                        .param("household", householdId).with(user(STRANGER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addingANameAlreadyOnTheListAsksForMoreOfIt() throws Exception {
        var list = ownList(ANNA);

        ops(ANNA, list, 0, add("Milch", "1 l"), add("milch", "500 ml"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].spec").value("1 l + 500 ml"));
    }

    @Test
    void askingForMoreCountsAsAddingItNow() throws Exception {
        var list = ownList(ANNA);
        ops(ANNA, list, 0, add("Milch", "1 l"));
        ops(ANNA, list, 0, add("Brot", null));

        var body = ops(ANNA, list, 0, add("Milch", "500 ml")).andReturn().getResponse().getContentAsString();
        assertThat(addedAt(body, "Milch")).isAfter(addedAt(body, "Brot"));
    }

    @Test
    void aRetriedBatchIsAppliedOnce() throws Exception {
        var list = ownList(ANNA);
        var batch = List.of(add("Milch", "1 l"));

        ops(ANNA, list, 0, batch);
        ops(ANNA, list, 0, batch).andExpect(jsonPath("$.items[0].spec").value("1 l"));
    }

    @Test
    void aDeviceHearsOnlyWhatChangedSinceItsVersionIncludingDeletes() throws Exception {
        var list = ownList(ANNA);
        var bread = UUID.randomUUID().toString();
        var body = ops(ANNA, list, 0, add(bread, "Brot", null), add("Butter", null))
                .andReturn().getResponse().getContentAsString();
        long version = ((Number) JsonPath.read(body, "$.version")).longValue();

        ops(ANNA, list, version, op("DELETE", bread))
                .andExpect(jsonPath("$.full").value(false))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(bread))
                .andExpect(jsonPath("$.items[0].deleted").value(true));
        changes(ANNA, list, 0)
                .andExpect(jsonPath("$.full").value(true))
                .andExpect(jsonPath("$.items[*].name", containsInAnyOrder("Butter")));
    }

    @Test
    void whatWasBoughtComesBackWhenAddedAgain() throws Exception {
        var list = ownList(ANNA);
        var eggs = UUID.randomUUID().toString();
        ops(ANNA, list, 0, add(eggs, "Eier", "6"), op("BUY", eggs))
                .andExpect(jsonPath("$.items[0].status").value("BOUGHT"));

        ops(ANNA, list, 0, add("Eier", "10"))
                .andExpect(jsonPath("$.items[0].id").value(eggs))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].spec").value("10"));
    }

    @Test
    void typedNamesArePlacedInTheirAisle() throws Exception {
        var list = ownList(ANNA);

        ops(ANNA, list, 0, add("Klopapier", null), add("Weizenmehl", "1 kg"), add("Zauberpulver", null))
                .andExpect(jsonPath("$.items[?(@.name == 'Klopapier')].aisle").value("HOUSEHOLD"))
                .andExpect(jsonPath("$.items[?(@.name == 'Klopapier')].icon").value("roll_of_paper"))
                .andExpect(jsonPath("$.items[?(@.name == 'Weizenmehl')].aisle").value("BAKING"))
                .andExpect(jsonPath("$.items[?(@.name == 'Zauberpulver')].aisle").value("OTHER"));
    }

    @Test
    void aNameAnAdministratorSaidIsNoFoodIsNotPlacedAsOne() throws Exception {
        var list = ownList(ANNA);
        nameRules.notAFood("Weizenmehl", userRepository.findByEmailAddress(ANNA));

        ops(ANNA, list, 0, add("Weizenmehl", null))
                .andExpect(jsonPath("$.items[0].aisle").value("OTHER"));
    }

    @Test
    void anAislePickedByHandSurvivesARename() throws Exception {
        var list = ownList(ANNA);
        var item = UUID.randomUUID().toString();
        ops(ANNA, list, 0, add(item, "Weizenmehl", null),
                new Op(UUID.randomUUID().toString(), "UPDATE", item, null, null, "DRUGSTORE"),
                new Op(UUID.randomUUID().toString(), "UPDATE", item, "Dinkelmehl", null, null))
                .andExpect(jsonPath("$.items[0].name").value("Dinkelmehl"))
                .andExpect(jsonPath("$.items[0].aisle").value("DRUGSTORE"))
                .andExpect(jsonPath("$.items[0].aisleManual").value(true));
    }

    @Test
    void importedLinesRememberTheirMealsAndTeachStaples() throws Exception {
        var list = ownList(ANNA);
        var request = """
                { "lines": [ { "name": "Mehl", "spec": "500 g", "aisle": "BAKING",
                               "sources": [ { "title": "Pfannkuchen", "planDate": "2026-10-05" } ] } ],
                  "shown": [ { "name": "Mehl", "ticked": true }, { "name": "Salz", "ticked": false } ] }
                """;
        for (var import_ = 0; import_ < 3; import_++) {
            mockMvc.perform(post("/api/v1/shopping/lists/" + list + "/import").with(user(ANNA))
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andExpect(status().isOk());
        }

        changes(ANNA, list, 0)
                .andExpect(jsonPath("$.items[0].spec").value("500 g + 500 g + 500 g"))
                .andExpect(jsonPath("$.items[0].sources[0].title").value("Pfannkuchen"));
        mockMvc.perform(get("/api/v1/shopping/staples").with(user(ANNA)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Salz")));
    }

    @Test
    void theDefaultListStaysAndOthersComeAndGo() throws Exception {
        mockMvc.perform(delete("/api/v1/shopping/lists/" + ownList(ANNA)).with(user(ANNA)))
                .andExpect(status().isConflict());

        var body = mockMvc.perform(post("/api/v1/shopping/lists").param("household", householdId).with(user(BERT))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Baumarkt\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var diy = ((Number) JsonPath.read(body, "$.id")).longValue();
        mockMvc.perform(put("/api/v1/shopping/lists/" + diy).param("household", householdId).with(user(ANNA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Garten\"}"))
                .andExpect(jsonPath("$.name").value("Garten"));
        mockMvc.perform(delete("/api/v1/shopping/lists/" + diy).param("household", householdId).with(user(ANNA)))
                .andExpect(status().isNoContent());
    }

    @Test
    void addingOffersWhatIsNoFoodNextToTheCatalogueTilesAndTheUnitWords() throws Exception {
        mockMvc.perform(get("/api/v1/shopping/vocabulary").with(user(ANNA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiles[?(@.key == 'toilet-paper')].aisle").value("HOUSEHOLD"))
                .andExpect(jsonPath("$.tiles[?(@.key == 'toilet-paper')].names.de[0]").value("Toilettenpapier"))
                .andExpect(jsonPath("$.unitWords", hasItems("kg", "netz", "dosen", "cans")));
    }

    private record Op(String opId, String type, String itemId, String name, String spec, String aisle) {

        String json() {
            return "{\"opId\":\"%s\",\"type\":\"%s\",\"itemId\":\"%s\"%s%s%s}".formatted(opId, type, itemId,
                    field("name", name), field("spec", spec), field("aisle", aisle));
        }

        private static String field(String key, String value) {
            return value == null ? "" : ",\"" + key + "\":\"" + value + "\"";
        }
    }

    private static Op add(String name, String spec) {
        return add(UUID.randomUUID().toString(), name, spec);
    }

    private static Op add(String itemId, String name, String spec) {
        return new Op(UUID.randomUUID().toString(), "ADD", itemId, name, spec, null);
    }

    private static Op op(String type, String itemId) {
        return new Op(UUID.randomUUID().toString(), type, itemId, null, null, null);
    }

    private ResultActions ops(String who, long list, long since, Op... ops) throws Exception {
        return ops(who, list, since, List.of(ops));
    }

    private ResultActions ops(String who, long list, long since, List<Op> ops) throws Exception {
        var body = "{\"ops\":[" + String.join(",", ops.stream().map(Op::json).toList()) + "]}";
        return mockMvc.perform(post("/api/v1/shopping/lists/" + list + "/ops").param("since", String.valueOf(since))
                        .param("household", householdOf(list)).with(user(who))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private ResultActions changes(String who, long list, long since) throws Exception {
        return mockMvc.perform(get("/api/v1/shopping/lists/" + list + "/changes").param("since", String.valueOf(since))
                        .param("household", householdOf(list)).with(user(who)))
                .andExpect(status().isOk());
    }

    private String householdOf(long list) {
        var household = listRepository.findById(list).orElseThrow().getHousehold();
        return household == null ? null : household.getId();
    }

    private long ownList(String who) throws Exception {
        return listOf(who, "$[?(@.householdId == null)].id");
    }

    private long householdList(String who) throws Exception {
        return listOf(who, "$[?(@.householdId != null)].id");
    }

    private long listOf(String who, String path) throws Exception {
        var body = mockMvc.perform(get("/api/v1/shopping/lists").with(user(who)))
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, path);
        return ids.get(0).longValue();
    }

    private static Instant addedAt(String changes, String name) {
        List<String> times = JsonPath.read(changes, "$.items[?(@.name == '" + name + "')].addedAt");
        return Instant.parse(times.get(0));
    }
}
