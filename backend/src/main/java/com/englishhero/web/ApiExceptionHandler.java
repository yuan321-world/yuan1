package com.englishhero.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception exception, HttpServletRequest request) throws Exception {
        // Let the security chain retain authentication/authorization semantics.
        if (exception instanceof AccessDeniedException || exception instanceof AuthenticationException) throw exception;
        if (exception instanceof ErrorResponse error && !error.getStatusCode().is5xxServerError()) {
            return ResponseEntity.status(error.getStatusCode()).build();
        }
        String traceId = (String) request.getAttribute(RequestTraceFilter.TRACE_ATTRIBUTE);
        log.error("internal_error traceId={} category={}", traceId, exception.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(
            new ApiErrorResponse("INTERNAL_ERROR", "服务暂不可用，请稍后重试", traceId));
    }
}
