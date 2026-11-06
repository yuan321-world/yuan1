package com.englishhero.web;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@Order(-10)
@RestControllerAdvice
public class BusinessExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> handle(BusinessException e,HttpServletRequest r){return ResponseEntity.status(e.status).header("Cache-Control","no-store").body(new ApiExceptionHandler.IdentityError(e.code,e.getMessage(),(String)r.getAttribute(RequestTraceFilter.TRACE_ATTRIBUTE),e.fields));}
}
