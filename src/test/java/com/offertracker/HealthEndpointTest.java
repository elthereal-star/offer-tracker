package com.offertracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.endpoint.health.show-details=never",
        "management.endpoint.health.show-components=never",
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class HealthEndpointTest {
    @Autowired MockMvc mockMvc;

    @Test
    void exposesHealthWithoutInfrastructureDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.matchesPattern(
                        "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));
    }

    @Test
    void preservesOnlyWellFormedCallerRequestIds() throws Exception {
        mockMvc.perform(get("/actuator/health").header("X-Request-Id", "11111111-2222-3333-4444-555555555555"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "11111111-2222-3333-4444-555555555555"));

        mockMvc.perform(get("/actuator/health").header("X-Request-Id", "not-a-uuid"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.matchesPattern(
                        "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));
    }
}
