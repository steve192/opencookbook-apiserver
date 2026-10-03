package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.entities.account.Invitation;
import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.repositories.InvitationRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.SecretTokens;
import com.sterul.opencookbookapiserver.services.UserService;
import com.sterul.opencookbookapiserver.services.exceptions.LastAdministratorException;

/** The first visitor creates the administrator; until then the app stays closed. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class SetupIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private InvitationRepository invitationRepository;
    @Autowired
    private UserService userService;

    @BeforeEach
    void startWithANewInstance() {
        userRepository.deleteAll();
    }

    @Test
    void aNewInstanceSaysSoAndRefusesSignups() throws Exception {
        mockMvc.perform(get("/api/v1/instance"))
                .andExpect(jsonPath("$.setupRequired").value(true))
                .andExpect(jsonPath("$.signupMode").value("OPEN"))
                .andExpect(jsonPath("$.mailEnabled").value(false));

        mockMvc.perform(post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"early@example.com\",\"password\":\"a-password\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETUP_REQUIRED"));
        assertNull(userRepository.findByEmailAddress("early@example.com"));
    }

    @Test
    void theFirstVisitorBecomesAnActiveAdministratorWhoCanSignIn() throws Exception {
        mockMvc.perform(post("/api/v1/setup")
                .header("Accept-Language", "de-DE,de;q=0.9")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("first@example.com")))
                .andExpect(status().isNoContent());

        var administrator = userRepository.findByEmailAddress("first@example.com");
        assertTrue(administrator.isActivated());
        assertEquals(Role.ADMIN, administrator.getRoles());
        assertEquals("de", administrator.getLanguage());

        mockMvc.perform(get("/api/v1/instance")).andExpect(jsonPath("$.setupRequired").value(false));
        mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials("first@example.com")))
                .andExpect(status().isOk());
    }

    @Test
    void onceSetUpTheSetupIsRefused() throws Exception {
        setUp("first@example.com").andExpect(status().isNoContent());

        setUp("second@example.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETUP_COMPLETED"));
        assertNull(userRepository.findByEmailAddress("second@example.com"));
    }

    @Test
    void aLockedAdministratorDoesNotCountAsSetUp() throws Exception {
        var locked = TestAccounts.ensure(userRepository, "locked-admin@example.com");
        locked.setRoles(Role.ADMIN);
        locked.setActivated(false);
        userRepository.save(locked);

        mockMvc.perform(get("/api/v1/instance")).andExpect(jsonPath("$.setupRequired").value(true));
        setUp("new-admin@example.com").andExpect(status().isNoContent());
    }

    @Test
    void ofSimultaneousFirstVisitorsExactlyOneBecomesAdministrator() {
        var visitors = 8;
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(visitors);
        try {
            var answers = IntStream.range(0, visitors)
                    .mapToObj(visitor -> executor.submit((Callable<Integer>) () -> {
                        start.await();
                        return setUp("visitor-" + visitor + "@example.com")
                                .andReturn().getResponse().getStatus();
                    }))
                    .toList();
            start.countDown();

            var statuses = answers.stream().map(SetupIntegrationTest::await).toList();
            assertEquals(1, statuses.stream().filter(answer -> answer == 204).count(), statuses.toString());
            assertEquals(visitors - 1, statuses.stream().filter(answer -> answer == 409).count(), statuses.toString());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, userRepository.countByRolesAndActivated(Role.ADMIN, true));
    }

    @Test
    void setupIsRequiredEvenForAValidInvitation() throws Exception {
        var invitation = invitationRepository.save(Invitation.builder()
                .id(SecretTokens.generate())
                .expiresAt(Instant.now().plus(Duration.ofDays(1)))
                .build());

        mockMvc.perform(post("/api/v1/users/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailAddress\":\"early@example.com\",\"password\":\"a-password\",\"invitation\":\"%s\"}"
                        .formatted(invitation.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETUP_REQUIRED"));
        assertTrue(invitationRepository.findById(invitation.getId()).isPresent());
    }

    @Test
    void ofTwoAdministratorsRemovingEachOtherExactlyOneSucceeds() {
        for (var round = 0; round < 5; round++) {
            var first = administrator("first-" + round + "@example.com");
            var second = administrator("second-" + round + "@example.com");
            var start = new CountDownLatch(1);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var removals = List.of(
                        executor.submit(deactivation(start, first.getUserId())),
                        executor.submit(deactivation(start, second.getUserId())));
                start.countDown();

                var failures = removals.stream().map(SetupIntegrationTest::await).toList();
                assertEquals(1, failures.stream().filter(Objects::isNull).count(), failures.toString());
                assertEquals(1, failures.stream().filter(LastAdministratorException.class::isInstance).count(),
                        failures.toString());
            } finally {
                executor.shutdownNow();
            }
            assertEquals(1, userRepository.countByRolesAndActivated(Role.ADMIN, true));
            userRepository.deleteAll();
        }
    }

    @Test
    void anAddressThatIsNotOneIsRefused() throws Exception {
        setUp("not an address")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private CookpalUser administrator(String emailAddress) {
        var administrator = TestAccounts.ensure(userRepository, emailAddress);
        administrator.setRoles(Role.ADMIN);
        return userRepository.save(administrator);
    }

    /** @return what the deactivation threw, or null */
    private Callable<Exception> deactivation(CountDownLatch start, Long userId) {
        return () -> {
            start.await();
            try {
                userService.setUserActivation(userId, false);
                return null;
            } catch (LastAdministratorException e) {
                return e;
            }
        };
    }

    private ResultActions setUp(String emailAddress) throws Exception {
        return mockMvc.perform(post("/api/v1/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentials(emailAddress)));
    }

    private static String credentials(String emailAddress) {
        return "{\"emailAddress\":\"" + emailAddress + "\",\"password\":\"a-password\"}";
    }

    private static <T> T await(Future<T> answer) {
        try {
            return answer.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
