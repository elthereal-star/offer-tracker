package com.offertracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest(properties = "offer-tracker.auth.required=true")
@AutoConfigureMockMvc
class AuthenticationContextFilterTest {
    @Autowired MockMvc mockMvc;

    @Test
    void rejectsMalformedBearerTokenWithJson401() throws Exception {
        mockMvc.perform(get("/api/companies").header("Authorization", "Bearer"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void requiresAuthenticationForBusinessApiWhenEnabled() throws Exception {
        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void keepsAuthEndpointsPublicWhenAuthenticationIsRequired() throws Exception {
        mockMvc.perform(post("/api/auth/verification-codes")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
