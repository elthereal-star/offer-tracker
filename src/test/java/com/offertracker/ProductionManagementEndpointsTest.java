package com.offertracker;

import com.offertracker.config.ProductionAuthenticationGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "server.port=8080",
        "management.server.port=8080",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:management-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "spring.data.redis.password=",
        "spring.data.redis.ssl.enabled=false",
        "offer-tracker.storage.type=local",
        "offer-tracker.auth.jwt-secret=test-production-jwt-secret-at-least-32-characters",
        "offer-tracker.auth.config-encryption-key=test-production-ai-config-key-at-least-32-characters"
})
@ActiveProfiles("production")
@AutoConfigureMockMvc
class ProductionManagementEndpointsTest {
    @Autowired MockMvc mockMvc;
    @Autowired Environment environment;
    @Autowired ApplicationContext context;

    @Test
    void exposesSafeProbeGroupsAndPrometheusOnlyInProductionProfile() throws Exception {
        org.junit.jupiter.api.Assertions.assertTrue(
                context.getBeanNamesForType(ProductionAuthenticationGuard.class).length > 0,
                "Production authentication guard was not configured");
        org.junit.jupiter.api.Assertions.assertEquals("health,prometheus",
                environment.getProperty("management.endpoints.web.exposure.include"));
        org.junit.jupiter.api.Assertions.assertTrue(context.getBeanNamesForType(PrometheusScrapeEndpoint.class).length > 0,
                "Prometheus scrape endpoint bean was not configured");
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components").doesNotExist());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));

        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Request-Id", org.hamcrest.Matchers.matchesPattern(
                                "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));
    }
}
