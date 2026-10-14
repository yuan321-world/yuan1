package com.englishhero.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {
    public static final String TRACE_ATTRIBUTE = RequestTraceFilter.class.getName() + ".traceId";
    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString();
        String previous = MDC.get("traceId");
        request.setAttribute(TRACE_ATTRIBUTE, traceId);
        response.setHeader("X-Request-Id", traceId);
        MDC.put("traceId", traceId);
        long start = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            failed = true;
            throw exception;
        } finally {
            // Never log query strings, bodies, headers, credentials or exception messages.
            String path = request.getRequestURI().replaceAll("[\\r\\n\\t]", "_");
            if (path.length() > 200) path = path.substring(0, 200);
            log.info("request traceId={} method={} path={} status={} durationMs={}", traceId,
                request.getMethod(), path, failed ? 500 : response.getStatus(), (System.nanoTime() - start) / 1_000_000);
            if (previous == null) MDC.remove("traceId"); else MDC.put("traceId", previous);
        }
    }
}
