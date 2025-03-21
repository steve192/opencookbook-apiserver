package com.sterul.opencookbookapiserver.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class CustomControllerAdviceTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testThatInvalidRequestFormatProduces400() throws Exception {
        mockMvc.perform(post("/api/v1/users/resetPassword")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}