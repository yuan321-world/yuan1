package com.englishhero.vocabulary;
import com.englishhero.identity.SessionPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/vocabulary/learning/words/{id}/difficulty")
public class WordDifficultyController {
 private final WordDifficultyService service;public WordDifficultyController(WordDifficultyService service){this.service=service;}
 @GetMapping public ResponseEntity<?> get(Authentication a,@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.state((SessionPrincipal)a.getPrincipal(),id));}
 @PutMapping public ResponseEntity<?> put(Authentication a,@PathVariable String id,@RequestBody WordDifficultyService.Input input){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.update((SessionPrincipal)a.getPrincipal(),id,input));}
}
