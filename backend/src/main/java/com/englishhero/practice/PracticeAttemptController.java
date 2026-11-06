package com.englishhero.practice;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import static com.englishhero.practice.PracticeDtos.*;
@RestController @RequestMapping("/api/v1/practice")
public class PracticeAttemptController {
 private final PracticeAttemptService service;
 public PracticeAttemptController(PracticeAttemptService service){this.service=service;}
 @PostMapping("/sets/{id}/attempts")public ResponseEntity<?> start(Authentication a,@PathVariable String id,@RequestBody StartInput d){return PracticeAdminController.reply(service.start(PracticeAdminController.actor(a),id,d));}
 @GetMapping("/attempts/{id}")public ResponseEntity<?> get(Authentication a,@PathVariable String id){return PracticeAdminController.reply(service.get(PracticeAdminController.actor(a),id));}
 @PutMapping("/attempts/{id}/draft")public ResponseEntity<?> draft(Authentication a,@PathVariable String id,@RequestBody DraftAnswersInput d){return PracticeAdminController.reply(service.saveDraft(PracticeAdminController.actor(a),id,d));}
 @PostMapping("/attempts/{id}/submit")public ResponseEntity<?> submit(Authentication a,@PathVariable String id,@RequestBody SubmitInput d){return PracticeAdminController.reply(service.submit(PracticeAdminController.actor(a),id,d));}
 @PostMapping("/attempts/{id}/retry-mistakes")public ResponseEntity<?> retry(Authentication a,@PathVariable String id,@RequestBody RetryInput d){return PracticeAdminController.reply(service.retryMistakes(PracticeAdminController.actor(a),id,d));}
 @GetMapping("/history")public ResponseEntity<?> history(Authentication a,@RequestParam Map<String,String> p){var q=new HashMap<>(p);q.remove("status");return PracticeAdminController.reply(service.history(PracticeAdminController.actor(a),PracticeQueries.parse(q,false),p.getOrDefault("status",p.get("state"))));}
 @GetMapping("/mistakes")public ResponseEntity<?> mistakes(Authentication a,@RequestParam Map<String,String> p){var q=new HashMap<>(p);q.remove("status");q.remove("state");return PracticeAdminController.reply(service.mistakes(PracticeAdminController.actor(a),PracticeQueries.parse(q,false),p.getOrDefault("status","PENDING")));}
}
