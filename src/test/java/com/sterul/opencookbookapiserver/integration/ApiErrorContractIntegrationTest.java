package com.sterul.opencookbookapiserver.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.sterul.opencookbookapiserver.repositories.UserRepository;

/**
 * Every failure answers in the one shape clients read, and none of them says anything about the
 * inside of this server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class ApiErrorContractIntegrationTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void aBodyMissingItsFieldsIsRejectedWithTheFieldsNamed() throws Exception {
        mockMvc.perform(post("/api/v1/users/resetPassword")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void aBodyThatIsNotJsonIsRejectedWithoutQuotingIt() throws Exception {
        mockMvc.perform(post("/api/v1/users/resetPassword")
                .contentType(MediaType.APPLICATION_JSON)
                .content("not json at all"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("The request could not be understood"));
    }

    @Test
    void aRequestWithoutATokenIsAnsweredInTheSameShapeAsEverythingElse() throws Exception {
        mockMvc.perform(get("/api/v1/recipes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void anUnknownPathIsNotFoundRatherThanAServerError() throws Exception {
        mockMvc.perform(get("/api/v1/instance/there-is-no-such-thing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void anEndpointCalledWithTheWrongMethodSaysSo() throws Exception {
        mockMvc.perform(get("/api/v1/users/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    /**
     * A handler of our own for plain Exception would out-match the framework's mapping for this
     * and answer 500 - telling the caller to retry a url that can never work, and writing a
     * stack trace for somebody else's typo.
     */
    @Test
    @WithMockUser(username = "someone@example.com")
    void aPathVariableThatWillNotParseIsTheCallersMistakeNotOurs() throws Exception {
        mockMvc.perform(get("/api/v1/recipes/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    /**
     * Authentication that could not be carried out at all. Reporting the database being down as
     * a rejected sign in would send everybody to reset a password that was never the problem.
     */
    @Test
    void anOutageBehindTheLoginIsNotReportedAsARejectedSignIn() throws Exception {
        when(userRepository.findByEmailAddress(any()))
                .thenThrow(new DataAccessResourceFailureException("database is down"));

        mockMvc.perform(post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"emailAddress": "someone@example.com", "password": "whatever"}
                        """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
