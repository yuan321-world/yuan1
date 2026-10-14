package com.englishhero.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.slf4j.MDC;
import static org.junit.jupiter.api.Assertions.*;

class RequestTraceFilterTest {
    @Test void logsExcludeQueryHeadersAndRequestBody() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(RequestTraceFilter.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            var request = new MockHttpServletRequest("POST", "/api/v1/example");
            request.setQueryString("password=private-query");
            request.addHeader("Cookie", "private-cookie");
            request.setContent("private-body".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            new RequestTraceFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
            var message = appender.list.getFirst().getFormattedMessage();
            assertTrue(message.contains("path=/api/v1/example"));
            assertFalse(message.contains("private"));
            assertFalse(message.contains("password"));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
    @Test void traceIsServerGeneratedAvailableInChainAndClearedAfterRequest() throws Exception {
        var filter = new RequestTraceFilter();
        var request = new MockHttpServletRequest("GET", "/api/v1/system/status");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            assertEquals(response.getHeader("X-Request-Id"), MDC.get("traceId"));
            assertEquals(MDC.get("traceId"), req.getAttribute(RequestTraceFilter.TRACE_ATTRIBUTE));
            response.setStatus(503);
        });
        assertEquals(503, response.getStatus());
        assertNull(MDC.get("traceId"));
    }
    @Test void traceIsClearedEvenWhenChainThrows() {
        var filter = new RequestTraceFilter();
        assertThrows(jakarta.servlet.ServletException.class, () -> filter.doFilter(
            new MockHttpServletRequest("GET", "/api/v1/broken"), new MockHttpServletResponse(),
            (req, res) -> { throw new jakarta.servlet.ServletException("private detail"); }));
        assertNull(MDC.get("traceId"));
    }
}
