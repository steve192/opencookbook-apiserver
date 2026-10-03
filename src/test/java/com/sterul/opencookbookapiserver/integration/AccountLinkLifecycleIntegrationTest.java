package com.sterul.opencookbookapiserver.integration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.repositories.ActivationLinkRepository;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.services.EmailService;
import com.sterul.opencookbookapiserver.services.UserService;
import com.sterul.opencookbookapiserver.services.exceptions.PasswordResetLinkNotExistingException;

/** What an administrator changing or deleting an account does to the links and mails around it. */
@SpringBootTest(properties = {
        "opencookbook.smtp-host=smtp.cookpal.invalid",
        "opencookbook.instanceURL=https://cookpal.invalid",
        "opencookbook.mail-from=cookpal@cookpal.invalid"
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class AccountLinkLifecycleIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ActivationLinkRepository activationLinks;
    @Autowired
    private TransactionTemplate transaction;

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        TestInstance.setUp(userRepository);
    }

    @Test
    void correctingTheAddressOfAPendingAccountInvalidatesItsLinks() throws Exception {
        var pending = userService.createUser("typo@cookpal.invalid", "a-password", false, null);
        var activation = userService.createActivationLink(pending);
        var reset = userService.createPasswordResetForAdministrator(pending.getUserId()).link();
        var resetId = reset.substring(reset.indexOf("id=") + 3);

        userService.updateUser(pending.getUserId(), "fixed@cookpal.invalid", false, null);

        mockMvc.perform(get("/api/v1/users/activate").param("activationId", activation.getId()))
                .andExpect(status().is4xxClientError());
        assertTrue(activationLinks.findById(activation.getId()).isEmpty());
        assertThrows(PasswordResetLinkNotExistingException.class,
                () -> userService.resetPassword("another-password", resetId));
    }

    @Test
    void savingAccountWithoutChangingTheAddressKeepsItsLink() {
        var pending = userService.createUser("same@cookpal.invalid", "a-password", false, null);
        var activation = userService.createActivationLink(pending);

        userService.updateUser(pending.getUserId(), "same@cookpal.invalid", false, Role.ADMIN);

        assertTrue(activationLinks.findById(activation.getId()).isPresent());
    }


    @Test
    void aLeftoverLinkOfAnActivatedAccountIsNotMailedAgain() throws Exception {
        var active = userService.createUser("active@cookpal.invalid", "a-password", true, null);
        userService.createActivationLink(active);

        userService.resendActivationLink("active@cookpal.invalid");

        verify(emailService, never()).sendActivationMail(any());
    }
    @Test
    void theAccountDeletedMailIsSentOnlyOnceTheDeletionIsCommitted() throws Exception {
        var leaving = userService.createUser("leaving@cookpal.invalid", "a-password", true, null);

        transaction.executeWithoutResult(status -> {
            userService.deleteUser(userService.getUserById(leaving.getUserId()));
            try {
                verify(emailService, never()).sendAccountDeletedMail(any(), any());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        verify(emailService).sendAccountDeletedMail(eq("leaving@cookpal.invalid"), any(Locale.class));
    }

    @Test
    void noMailIsSentForADeletionThatIsRolledBack() throws Exception {
        var staying = userService.createUser("staying@cookpal.invalid", "a-password", true, null);

        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            userService.deleteUser(userService.getUserById(staying.getUserId()));
            throw new IllegalStateException("rolled back");
        }));

        verify(emailService, never()).sendAccountDeletedMail(any(), any());
    }
}
