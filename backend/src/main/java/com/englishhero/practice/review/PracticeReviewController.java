package com.englishhero.practice.review;
import org.springframework.web.bind.annotation.*;import org.springframework.http.*;import org.springframework.security.core.Authentication;import com.englishhero.identity.SessionPrincipal;
@RestController @RequestMapping("/api/v1/practice/subjective-reviews")
public class PracticeReviewController {
 private final PracticeReviewService service;public PracticeReviewController(PracticeReviewService service){this.service=service;}
 private SessionPrincipal actor(Authentication a){return a!=null&&a.getPrincipal() instanceof SessionPrincipal p?p:null;}
 @PostMapping public ResponseEntity<?> create(Authentication a,@RequestBody PracticeReviewDtos.CreateReview in){return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(service.create(actor(a),in));}
 @GetMapping("/{id}") public ResponseEntity<?> get(Authentication a,@PathVariable String id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(actor(a),id));}
 @GetMapping public ResponseEntity<?> history(Authentication a,@RequestParam String sourceType,@RequestParam String attemptId,@RequestParam String slot){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history(actor(a),sourceType,attemptId,slot));}
}
