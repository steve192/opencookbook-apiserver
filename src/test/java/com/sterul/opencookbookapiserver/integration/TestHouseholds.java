package com.sterul.opencookbookapiserver.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/** Starts and joins households the way people do, through the API. */
final class TestHouseholds {

    private TestHouseholds() {
    }

    /** @return the new household's id */
    static String start(MockMvc mockMvc, String founder, String name) throws Exception {
        var body = mockMvc.perform(post("/api/v1/households").with(user(founder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"shareRecipes\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    static void join(MockMvc mockMvc, String householdId, String inviter, String joiner) throws Exception {
        var invite = mockMvc.perform(post("/api/v1/households/" + householdId + "/invites").with(user(inviter)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/v1/household-invites/" + JsonPath.read(invite, "$.token") + "/accept")
                        .with(user(joiner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareRecipes\": false}"))
                .andExpect(status().isOk());
    }

    /** Puts the member's cookbook into the household. */
    static void share(MockMvc mockMvc, String householdId, String member) throws Exception {
        mockMvc.perform(put("/api/v1/households/" + householdId + "/sharing").with(user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareRecipes\": true}"))
                .andExpect(status().isOk());
    }
}
