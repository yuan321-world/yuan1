package com.englishhero.reading.quiz;

import com.englishhero.identity.SessionPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static com.englishhero.reading.quiz.AttemptDtos.*;
@RestController @RequestMapping("/api/v1/reading")
public class AttemptController {
 private final AttemptService service;public AttemptController(AttemptService service){this.service=service;}
 private SessionPrincipal actor(Authentication a){return (SessionPrincipal)a.getPrincipal();}
 private ResponseEntity<?> reply(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
 @GetMapping("/articles/{a}/versions/{v}/practice")public ResponseEntity<?> state(Authentication auth,@PathVariable String a,@PathVariable String v){return reply(service.state(actor(auth),a,v));}
 @GetMapping("/articles/{a}/versions/{v}/practice/history")public ResponseEntity<?> history(Authentication auth,@PathVariable String a,@PathVariable String v,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="6")int pageSize){return reply(service.history(actor(auth),a,v,page,pageSize));}
 @PostMapping("/quizzes/{id}/attempts")public ResponseEntity<?> start(Authentication auth,@PathVariable String id,@RequestBody StartInput input){return reply(service.start(actor(auth),id,input));}
 @GetMapping("/attempts/{id}")public ResponseEntity<?> get(Authentication auth,@PathVariable String id){return reply(service.get(actor(auth),id));}
 @PutMapping("/attempts/{id}/answers/{q}")public ResponseEntity<?> choice(Authentication auth,@PathVariable String id,@PathVariable String q,@RequestBody ChoiceInput input){return reply(service.choice(actor(auth),id,q,input,false));}
 @PostMapping("/attempts/{id}/answers/{q}/submit")public ResponseEntity<?> submit(Authentication auth,@PathVariable String id,@PathVariable String q,@RequestBody ChoiceInput input){return reply(service.choice(actor(auth),id,q,input,true));}
}
