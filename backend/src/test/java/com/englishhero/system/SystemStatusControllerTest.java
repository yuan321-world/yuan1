package com.englishhero.system;

import com.englishhero.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = SystemStatusController.class, properties = "springdoc.api-docs.enabled=false",
    excludeAutoConfiguration = org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
class SystemStatusControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SystemStatusService service;
    @MockitoBean com.englishhero.identity.IdentityMapper identities;
    @MockitoBean com.englishhero.identity.IdentityService identityService;


    @Test void statusIsPublicAndMatchesContract() throws Exception {
        when(service.getStatus()).thenReturn(new SystemStatusResponse("UP", "english-hero", "UP"));
        mvc.perform(get("/api/v1/system/status"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.service").value("english-hero"))
            .andExpect(jsonPath("$.database").value("UP"));
    }
    @Test void unavailableDatabaseReturns503() throws Exception {
        when(service.getStatus()).thenReturn(new SystemStatusResponse("DOWN", "english-hero", "DOWN"));
        mvc.perform(get("/api/v1/system/status"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(header().exists("X-Request-Id"))
            .andExpect(content().json("{\"status\":\"DOWN\",\"service\":\"english-hero\",\"database\":\"DOWN\"}"));
    }
    @Test void unknownApiAndProductionDocsAreDenied() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized()).andExpect(header().exists("X-Request-Id"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
    }
    @Test void writesAreDeniedAndCsrfIsRetained() throws Exception {
        mvc.perform(post("/api/v1/system/status")).andExpect(status().isForbidden()).andExpect(header().exists("X-Request-Id"));
    }
}
