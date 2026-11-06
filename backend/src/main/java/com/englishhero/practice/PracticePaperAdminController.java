package com.englishhero.practice;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import static com.englishhero.practice.PracticeAdminController.*;
import static com.englishhero.practice.PracticePaperDtos.*;
@RestController @RequestMapping("/api/v1/admin/practice/papers")
public class PracticePaperAdminController {
 private final PracticePaperAdminService service;
 public PracticePaperAdminController(PracticePaperAdminService service){this.service=service;}
 @GetMapping public ResponseEntity<?> list(Authentication a){return reply(service.list(actor(a)));}
 @GetMapping("/components") public ResponseEntity<?> candidates(Authentication a,@RequestParam Map<String,String> q){return reply(service.candidates(actor(a),PracticePaperCatalogService.query(q)));}
 @PostMapping public ResponseEntity<?> create(Authentication a,@RequestBody AdminInput d){return reply(service.create(actor(a),d));}
 @GetMapping("/{id}") public ResponseEntity<?> get(Authentication a,@PathVariable String id){return reply(service.get(actor(a),id));}
 @GetMapping("/{id}/preview") public ResponseEntity<?> preview(Authentication a,@PathVariable String id){return reply(service.preview(actor(a),id));}
 @PutMapping("/{id}") public ResponseEntity<?> save(Authentication a,@PathVariable String id,@RequestBody AdminInput d){return reply(service.save(actor(a),id,d));}
 @PostMapping("/{id}/review") public ResponseEntity<?> review(Authentication a,@PathVariable String id,@RequestBody LockInput d){return reply(service.review(actor(a),id,d));}
 @PostMapping("/{id}/publish") public ResponseEntity<?> publish(Authentication a,@PathVariable String id,@RequestBody LockInput d){return reply(service.publish(actor(a),id,d));}
 @PostMapping("/{id}/unpublish") public ResponseEntity<?> unpublish(Authentication a,@PathVariable String id,@RequestBody LockInput d){return reply(service.unpublish(actor(a),id,d));}
 @GetMapping("/{id}/versions") public ResponseEntity<?> versions(Authentication a,@PathVariable String id){return reply(service.versions(actor(a),id));}
}
