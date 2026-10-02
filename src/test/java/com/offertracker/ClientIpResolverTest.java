package com.offertracker;

import com.offertracker.config.ClientIpResolver;
import com.offertracker.config.TrustedProxyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {
    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        ClientIpResolver resolver = new ClientIpResolver(new TrustedProxyProperties(List.of("10.0.0.0/8")));
        MockHttpServletRequest request = request("203.0.113.10", "198.51.100.8");
        assertEquals("203.0.113.10", resolver.resolve(request));
    }

    @Test
    void selectsFirstUntrustedAddressBehindTrustedProxyChain() {
        ClientIpResolver resolver = new ClientIpResolver(new TrustedProxyProperties(List.of("10.0.0.0/8")));
        MockHttpServletRequest request = request("10.0.0.5", "198.51.100.8, 10.0.0.9");
        assertEquals("198.51.100.8", resolver.resolve(request));
    }

    @Test
    void matchesConfiguredProxyNetwork() {
        ClientIpResolver resolver = new ClientIpResolver(new TrustedProxyProperties(List.of("192.0.2.0/24")));
        MockHttpServletRequest request = request("192.0.2.12", "198.51.100.8");
        assertEquals("198.51.100.8", resolver.resolve(request));
    }

    @Test
    void ignoresHostnamesInForwardedHeader() {
        ClientIpResolver resolver = new ClientIpResolver(new TrustedProxyProperties(List.of("10.0.0.0/8")));
        MockHttpServletRequest request = request("10.0.0.5", "localhost");
        assertEquals("10.0.0.5", resolver.resolve(request));
    }

    private MockHttpServletRequest request(String remote, String forwarded) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        request.addHeader("X-Forwarded-For", forwarded);
        return request;
    }
}
