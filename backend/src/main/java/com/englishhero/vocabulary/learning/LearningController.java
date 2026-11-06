package com.englishhero.vocabulary.learning;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import com.englishhero.identity.SessionPrincipal;
import static com.englishhero.vocabulary.learning.LearningDtos.*;
@RestController
@RequestMapping("/api/v1/vocabulary/learning")
public class LearningController {
 private final LearningService service;public LearningController(LearningService service){this.service=service;}
 private SessionPrincipal actor(Authentication a){return (SessionPrincipal)a.getPrincipal();}
 private ResponseEntity<?> reply(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON).body(body==null?"null":body);}
 @GetMapping("/overview")public ResponseEntity<?> overview(Authentication a){return reply(service.overview(actor(a)));}
 @GetMapping("/candidates")public ResponseEntity<?> candidates(Authentication a,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return reply(service.candidates(actor(a),page,pageSize));}
 @PostMapping("/words")public ResponseEntity<?> join(Authentication a,@RequestBody JoinInput input){service.join(actor(a),input);return reply(null);}
 @GetMapping("/words")public ResponseEntity<?> words(Authentication a,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return reply(service.learningWords(actor(a),page,pageSize));}
 @PatchMapping("/words/{id}")public ResponseEntity<?> active(Authentication a,@PathVariable String id,@RequestBody ActiveInput input){service.setActive(actor(a),id,input);return reply(null);}
 @PutMapping("/words/{id}/proficiency")public ResponseEntity<?> proficiency(Authentication a,@PathVariable String id,@RequestBody ProficiencyInput input){service.proficiency(actor(a),id,input);return reply(null);}
 @PutMapping("/settings")public ResponseEntity<?> settings(Authentication a,@RequestBody Settings input){return reply(service.settings(actor(a),input));}
 @GetMapping("/session")public ResponseEntity<?> current(Authentication a){return reply(service.current(actor(a)));}
 @GetMapping("/sessions/{id}")public ResponseEntity<?> result(Authentication a,@PathVariable String id){return reply(service.result(actor(a),id));}
 @PostMapping("/sessions")public ResponseEntity<?> start(Authentication a,@RequestBody Command input){return reply(service.start(actor(a),input));}
 @PostMapping("/sessions/{id}/{operation:feedback|next|state}")public ResponseEntity<?> mutate(Authentication a,@PathVariable String id,@PathVariable String operation,@RequestBody Command input){return reply(service.mutate(actor(a),id,operation,input));}
}
