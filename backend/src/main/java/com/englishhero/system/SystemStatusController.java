package com.englishhero.system;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {
    private final SystemStatusService service;

    public SystemStatusController(SystemStatusService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public ResponseEntity<SystemStatusResponse> status() {
        var result = service.getStatus();
        return ResponseEntity.status("UP".equals(result.status()) ? 200 : 503)
            .cacheControl(CacheControl.noStore()).body(result);
    }
}
