package com.englishhero.practice;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import static com.englishhero.practice.PracticeAdminController.*;
import static com.englishhero.practice.PracticePaperDtos.*;
@RestController @RequestMapping("/api/v1/practice")
public class PracticePaperController {
 private final PracticePaperCatalogService catalog;private final PracticePaperAttemptService service;private final PracticePaperMistakeService mistakes;
 public PracticePaperController(PracticePaperCatalogService catalog,PracticePaperAttemptService service,PracticePaperMistakeService mistakes){this.catalog=catalog;this.service=service;this.mistakes=mistakes;}
 @GetMapping("/papers") public ResponseEntity<?> list(Authentication a,@RequestParam Map<String,String> q){return reply(catalog.list(actor(a),PracticePaperCatalogService.query(q)));}
 @GetMapping("/papers/{id}") public ResponseEntity<?> detail(Authentication a,@PathVariable String id){return reply(catalog.get(actor(a),id));}
 @PostMapping("/papers/{id}/attempts") public ResponseEntity<?> start(Authentication a,@PathVariable String id,@RequestBody StartInput d){return reply(service.start(actor(a),id,d));}
 @GetMapping("/paper-attempts/{id}") public ResponseEntity<?> get(Authentication a,@PathVariable String id){return reply(service.get(actor(a),id));}
 @PutMapping("/paper-attempts/{id}/draft") public ResponseEntity<?> draft(Authentication a,@PathVariable String id,@RequestBody DraftInput d){return reply(service.save(actor(a),id,d));}
 @PostMapping("/paper-attempts/{id}/submit") public ResponseEntity<?> submit(Authentication a,@PathVariable String id,@RequestBody SubmitInput d){return reply(service.submit(actor(a),id,d));}
 @PostMapping("/paper-attempts/{id}/audio-ticket") public ResponseEntity<?> audio(Authentication a,@PathVariable String id){return reply(service.audioTicket(actor(a),id));}
 @PostMapping("/paper-attempts/{id}/retry-mistakes") public ResponseEntity<?> retryMistakes(Authentication a,@PathVariable String id,@RequestBody PracticePaperMistakeDtos.RetryInput d){return reply(mistakes.retry(actor(a),id,d));}
 @GetMapping("/paper-mistakes") public ResponseEntity<?> mistakes(Authentication a,@RequestParam Map<String,String> q){return reply(mistakes.list(actor(a),PracticePaperMistakeService.query(q)));}
 @GetMapping("/paper-history") public ResponseEntity<?> history(Authentication a,@RequestParam Map<String,String> q){return reply(service.history(actor(a),PracticePaperCatalogService.query(q)));}
}
