package com.ringout.api.config.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    private final RequestIdFilter requestIdFilter = new RequestIdFilter();

    @Test
    void usesRequestIdHeaderWhenPresent() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInFilterChain = new AtomicReference<>();

        request.addHeader(REQUEST_ID_HEADER, "client-request-id");

        requestIdFilter.doFilter(request, response, (servletRequest, servletResponse) ->
            requestIdInFilterChain.set(MDC.get(REQUEST_ID_MDC_KEY))
        );

        assertEquals("client-request-id", requestIdInFilterChain.get());
        assertEquals("client-request-id", response.getHeader(REQUEST_ID_HEADER));
        assertNull(MDC.get(REQUEST_ID_MDC_KEY));
    }

    @Test
    void createsRequestIdWhenHeaderIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdInFilterChain = new AtomicReference<>();

        requestIdFilter.doFilter(request, response, (servletRequest, servletResponse) ->
            requestIdInFilterChain.set(MDC.get(REQUEST_ID_MDC_KEY))
        );

        assertNotNull(requestIdInFilterChain.get());
        assertEquals(requestIdInFilterChain.get(), response.getHeader(REQUEST_ID_HEADER));
        assertNull(MDC.get(REQUEST_ID_MDC_KEY));
    }
}
