package com.sterul.opencookbookapiserver.integration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
class LegalDocumentIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "GET /api/v1/legal/{0}")
    @CsvSource({
            "terms,   Terms of use",
            "privacy, Privacy policy",
            "imprint, Imprint",
    })
    void eachDocumentIsPublicHtml(String document, String title) throws Exception {
        mockMvc.perform(get("/api/v1/legal/" + document))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().encoding("UTF-8"))
                .andExpect(content().string(containsString("<h1>" + title + "</h1>")));
    }

    @Test
    void anUnknownDocumentIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/legal/cookies"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
