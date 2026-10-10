package com.offertracker;

import com.offertracker.config.RequestCorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestCorrelationIdFilterTest {
    private final RequestCorrelationIdFilter filter = new RequestCorrelationIdFilter();

    @Test
    void bindsRequestIdToLogsOnlyForTheRequestLifetime() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> observedInChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> observedInChain.set(MDC.get("requestId")));

        assertEquals(response.getHeader(RequestCorrelationIdFilter.HEADER_NAME), observedInChain.get());
        assertNull(MDC.get("requestId"));
    }
}
