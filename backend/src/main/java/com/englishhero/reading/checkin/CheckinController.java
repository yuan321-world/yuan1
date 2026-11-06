package com.englishhero.reading.checkin;

import com.englishhero.identity.SessionPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/reading")
public class CheckinController {
 private final CheckinService service;
 public CheckinController(CheckinService service){this.service=service;}
 private ResponseEntity<?> reply(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
 @GetMapping("/checkins/week") public ResponseEntity<?> week(Authentication auth){return reply(service.week((SessionPrincipal)auth.getPrincipal()));}
 @GetMapping("/articles/{a}/versions/{v}/checkin") public ResponseEntity<?> status(Authentication auth,@PathVariable String a,@PathVariable String v){return reply(service.status((SessionPrincipal)auth.getPrincipal(),a,v));}
}
