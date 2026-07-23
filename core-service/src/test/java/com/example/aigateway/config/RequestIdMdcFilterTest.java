package com.example.aigateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdMdcFilterTest {
    private final RequestIdMdcFilter filter = new RequestIdMdcFilter();

    @Test
    void exposesGatewayRequestIdInsideCoreLogsAndCleansMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/health");
        request.addHeader(RequestIdMdcFilter.REQUEST_ID_HEADER, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdSeenByHandler = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                requestIdSeenByHandler.set(MDC.get(RequestIdMdcFilter.REQUEST_ID_MDC_KEY)));

        assertEquals("request-123", requestIdSeenByHandler.get());
        assertEquals("request-123", response.getHeader(RequestIdMdcFilter.REQUEST_ID_HEADER));
        assertNull(MDC.get(RequestIdMdcFilter.REQUEST_ID_MDC_KEY));
    }
}
