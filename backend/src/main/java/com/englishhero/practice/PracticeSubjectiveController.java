package com.englishhero.practice;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import static com.englishhero.practice.PracticeAdminController.*;
@RestController @RequestMapping("/api/v1")
public class PracticeSubjectiveController {
 private final PracticeSubjectiveAdminService admin;private final PracticeSubjectiveService service;
 public PracticeSubjectiveController(PracticeSubjectiveAdminService admin,PracticeSubjectiveService service){this.admin=admin;this.service=service;}
 @PostMapping("/admin/practice/subjective-sets") public ResponseEntity<?> create(Authentication a,@RequestBody PracticeSubjectiveDtos.AdminInput d){return reply(admin.create(actor(a),d));}
 @PutMapping("/admin/practice/sets/{id}/subjective") public ResponseEntity<?> saveContent(Authentication a,@PathVariable String id,@RequestBody PracticeSubjectiveDtos.AdminInput d){return reply(admin.save(actor(a),id,d));}
 @PostMapping("/practice/sets/{id}/subjective-attempts") public ResponseEntity<?> start(Authentication a,@PathVariable String id,@RequestBody PracticeDtos.StartInput d){return reply(service.start(actor(a),id,d));}
 @GetMapping("/practice/subjective-attempts/{id}") public ResponseEntity<?> get(Authentication a,@PathVariable String id){return reply(service.get(actor(a),id));}
 @PutMapping("/practice/subjective-attempts/{id}/draft") public ResponseEntity<?> draft(Authentication a,@PathVariable String id,@RequestBody PracticeSubjectiveDtos.DraftInput d){return reply(service.save(actor(a),id,d));}
 @PostMapping("/practice/subjective-attempts/{id}/submit") public ResponseEntity<?> submit(Authentication a,@PathVariable String id,@RequestBody PracticeSubjectiveDtos.SubmitInput d){return reply(service.submit(actor(a),id,d));}
 @PostMapping("/practice/subjective-attempts/{id}/revise") public ResponseEntity<?> revise(Authentication a,@PathVariable String id,@RequestBody PracticeDtos.RetryInput d){return reply(service.revise(actor(a),id,d));}
 @GetMapping("/practice/subjective-history") public ResponseEntity<?> history(Authentication a,@RequestParam Map<String,String> q){return reply(service.history(actor(a),PracticeQueries.parse(q,false)));}
}
