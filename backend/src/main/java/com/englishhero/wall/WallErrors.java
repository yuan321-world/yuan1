package com.englishhero.wall;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import com.englishhero.web.*;
@Order(-20) @RestControllerAdvice(assignableTypes={WallController.class,WallAdminController.class})
public class WallErrors {
 @ExceptionHandler(WallRateLimitException.class)public ResponseEntity<?> limited(WallRateLimitException e,HttpServletRequest request){return ResponseEntity.status(429).header("Cache-Control","no-store").header("Retry-After",Long.toString(e.retryAfter)).body(new ApiExceptionHandler.IdentityError(e.code,e.getMessage(),(String)request.getAttribute(RequestTraceFilter.TRACE_ATTRIBUTE),e.fields));}
}
