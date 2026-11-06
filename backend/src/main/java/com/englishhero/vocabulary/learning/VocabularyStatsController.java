package com.englishhero.vocabulary.learning;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import com.englishhero.identity.SessionPrincipal;
@RestController
public class VocabularyStatsController {
 private final VocabularyStatsService service;public VocabularyStatsController(VocabularyStatsService service){this.service=service;}
 @GetMapping("/api/v1/vocabulary/learning/stats")public ResponseEntity<?> stats(Authentication a){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.stats((SessionPrincipal)a.getPrincipal()));}
}
