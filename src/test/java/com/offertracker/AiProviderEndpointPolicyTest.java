package com.offertracker;

import com.offertracker.common.BusinessException;
import com.offertracker.service.AiProviderEndpointPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiProviderEndpointPolicyTest {
    @Test
    void productionAcceptsHttpsPublicHostname() {
        AiProviderEndpointPolicy policy = policy("production");

        assertEquals("https://api.example.com/v1",
                policy.normalize(" https://api.example.com/v1/ "));
    }

    @Test
    void productionRejectsHttpLocalAndPrivateAddressTargets() {
        AiProviderEndpointPolicy policy = policy("production");

        assertThrows(BusinessException.class, () -> policy.normalize("http://api.example.com/v1"));
        assertThrows(BusinessException.class, () -> policy.normalize("https://localhost/v1"));
        assertThrows(BusinessException.class, () -> policy.normalize("https://10.0.0.8/v1"));
        assertThrows(BusinessException.class, () -> policy.normalize("https://203.0.113.10/v1"));
        assertThrows(BusinessException.class, () -> policy.normalize("https://[::1]/v1"));
    }

    @Test
    void rejectsUserInfoQueryAndFragment() {
        AiProviderEndpointPolicy policy = policy();

        assertThrows(BusinessException.class, () -> policy.normalize("https://user:pass@api.example.com/v1"));
        assertThrows(BusinessException.class, () -> policy.normalize("https://api.example.com/v1?key=value"));
        assertThrows(BusinessException.class, () -> policy.normalize("https://api.example.com/v1#fragment"));
    }

    @Test
    void localProfileStillAllowsLocalHttpProviders() {
        AiProviderEndpointPolicy policy = policy();

        assertEquals("http://localhost:1234/v1", policy.normalize("http://localhost:1234/v1/"));
    }

    private AiProviderEndpointPolicy policy(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new AiProviderEndpointPolicy(environment);
    }
}
