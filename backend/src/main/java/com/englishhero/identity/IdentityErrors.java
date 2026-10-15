package com.englishhero.identity;

import com.englishhero.web.RequestTraceFilter;
import jakarta.servlet.http.*;
import java.io.IOException;

public final class IdentityErrors {
    private IdentityErrors() {}
    public static void write(HttpServletResponse response, HttpServletRequest request, int status, String code, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        // All arguments except trace are server constants; trace is server-generated UUID.
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"traceId\":\"" + request.getAttribute(RequestTraceFilter.TRACE_ATTRIBUTE) + "\"}");
    }
}
