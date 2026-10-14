package com.englishhero.web;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionHandlerTest {
    @RestController static class BrokenController {
        @GetMapping("/api/v1/broken") String broken() { throw new IllegalStateException("SQL password=secret"); }
        @GetMapping("/api/v1/checked") String checked() throws java.io.IOException { throw new java.io.IOException("private IO detail"); }
        @GetMapping("/api/v1/status-error") String statusError() { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "private reason"); }
        @GetMapping("/api/v1/not-found") String notFound() { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "private reason"); }
    }
    @Test void checkedInternalExceptionAlsoUsesSanitizedContract() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new BrokenController())
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestTraceFilter()).build();
        mvc.perform(get("/api/v1/checked"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("服务暂不可用，请稍后重试"));
    }
    @Test void internalStatusExceptionUsesPublicErrorButClientStatusIsPreserved() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new BrokenController())
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestTraceFilter()).build();
        mvc.perform(get("/api/v1/status-error")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
        mvc.perform(get("/api/v1/not-found")).andExpect(status().isNotFound());
    }
    @Test void internalErrorIsSanitizedAndTraceMatchesHeader() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new BrokenController())
            .setControllerAdvice(new ApiExceptionHandler()).addFilters(new RequestTraceFilter()).build();
        var result = mvc.perform(get("/api/v1/broken").header("X-Request-Id", "client-controlled"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("服务暂不可用，请稍后重试"))
            .andReturn();
        var trace = result.getResponse().getHeader("X-Request-Id");
        assertNotNull(trace);
        assertNotEquals("client-controlled", trace);
        assertTrue(result.getResponse().getContentAsString().contains(trace));
        assertFalse(result.getResponse().getContentAsString().contains("secret"));
        assertFalse(result.getResponse().getContentAsString().contains("SQL"));
    }
}
