package com.sterul.opencookbookapiserver.integration;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Testcontainers
class CustomControllerAdviceTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testThatInvalidRequestFormatProduces400() throws Exception {
        mockMvc.perform(post("/api/v1/users/resetPassword")
                .contentType("application/json")
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        result -> assertEquals(
                                "{\"status\":400,\"message\":\"validation error\",\"fieldErrors\":[{\"codes\":null,\"arguments\":null,\"defaultMessage\":\"must not be blank\",\"objectName\":\"passwordResetExecutionRequest\",\"field\":\"newPassword\",\"rejectedValue\":null,\"bindingFailure\":false,\"code\":null},{\"codes\":null,\"arguments\":null,\"defaultMessage\":\"must not be blank\",\"objectName\":\"passwordResetExecutionRequest\",\"field\":\"passwordResetId\",\"rejectedValue\":null,\"bindingFailure\":false,\"code\":null},{\"codes\":null,\"arguments\":null,\"defaultMessage\":\"must not be null\",\"objectName\":\"passwordResetExecutionRequest\",\"field\":\"newPassword\",\"rejectedValue\":null,\"bindingFailure\":false,\"code\":null},{\"codes\":null,\"arguments\":null,\"defaultMessage\":\"must not be null\",\"objectName\":\"passwordResetExecutionRequest\",\"field\":\"passwordResetId\",\"rejectedValue\":null,\"bindingFailure\":false,\"code\":null}]}",
                                result.getResponse().getContentAsString()));
    }
}