package com.englishhero.vocabulary.learning;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import com.englishhero.identity.SessionPrincipal;
@RestController @RequestMapping("/api/v1/vocabulary/learning")
public class WordbookController {
 private final WordbookService service;public WordbookController(WordbookService service){this.service=service;}
 private SessionPrincipal actor(Authentication a){return (SessionPrincipal)a.getPrincipal();}
 private ResponseEntity<?> reply(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
 @GetMapping("/wordbooks")public ResponseEntity<?> list(Authentication a){return reply(service.list(actor(a)));}
 @GetMapping("/source")public ResponseEntity<?> source(Authentication a){return reply(service.source(actor(a)));}
 @PutMapping("/source")public ResponseEntity<?> select(Authentication a,@RequestBody WordbookDtos.Selection input){return reply(service.select(actor(a),input));}
}
