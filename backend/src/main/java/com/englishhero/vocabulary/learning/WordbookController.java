package com.englishhero.vocabulary.learning;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import com.englishhero.identity.SessionPrincipal;
@RestController @RequestMapping("/api/v1/vocabulary/learning")
public class WordbookController {
 private final WordbookService service;private final WordbookWordsService words;public WordbookController(WordbookService service,WordbookWordsService words){this.service=service;this.words=words;}
 private SessionPrincipal actor(Authentication a){return (SessionPrincipal)a.getPrincipal();}
 private ResponseEntity<?> reply(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
 @GetMapping("/wordbooks")public ResponseEntity<?> list(Authentication a){return reply(service.list(actor(a)));}
 @GetMapping("/wordbooks/{book}/words")public ResponseEntity<?> words(Authentication a,@PathVariable String book,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="ALL")String status,@RequestParam(defaultValue="false")boolean collectedOnly,@RequestParam(defaultValue="BOOK")String sort,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="30")int pageSize){return reply(words.list(actor(a),book,q,status,collectedOnly,sort,page,pageSize));}
 @PutMapping("/wordbooks/{book}/words/{entry}/proficiency")public ResponseEntity<?> proficiency(Authentication a,@PathVariable String book,@PathVariable String entry,@RequestBody LearningDtos.ProficiencyInput input){return reply(words.proficiency(actor(a),book,entry,input));}
 @GetMapping("/source")public ResponseEntity<?> source(Authentication a){return reply(service.source(actor(a)));}
 @PutMapping("/source")public ResponseEntity<?> select(Authentication a,@RequestBody WordbookDtos.Selection input){return reply(service.select(actor(a),input));}
}
