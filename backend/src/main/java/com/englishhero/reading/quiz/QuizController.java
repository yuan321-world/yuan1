package com.englishhero.reading.quiz;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.HttpServletRequest;
import com.englishhero.identity.SessionPrincipal;
import com.englishhero.reading.ReadingDtos.LockInput;
import static com.englishhero.reading.quiz.QuizDtos.*;
@RestController @RequestMapping("/api/v1")
public class QuizController {
 private final QuizService service;private final GuestQuizService guests;
 public QuizController(QuizService service,GuestQuizService guests){this.service=service;this.guests=guests;}
 private SessionPrincipal actor(Authentication a){return a!=null&&a.getPrincipal() instanceof SessionPrincipal p?p:null;}
 private ResponseEntity<?> reply(int status,Object body){return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(body==null?"null":body);}
 @GetMapping("/reading/articles/{a}/versions/{v}/quiz")public ResponseEntity<?> published(Authentication auth,@PathVariable String a,@PathVariable String v){return reply(200,service.published(actor(auth),a,v));}
 @GetMapping("/admin/articles/{a}/versions/{v}/quiz")public ResponseEntity<?> admin(Authentication auth,@PathVariable String a,@PathVariable String v){return reply(200,service.get(actor(auth),a,v));}
 @PutMapping("/admin/articles/{a}/versions/{v}/quiz")public ResponseEntity<?> save(Authentication auth,@PathVariable String a,@PathVariable String v,@RequestBody Input input){return reply(200,service.save(actor(auth),a,v,input));}
 @PostMapping("/admin/reading/quizzes/{id}/publish")public ResponseEntity<?> publish(Authentication auth,@PathVariable String id,@RequestBody LockInput input){return reply(200,service.status(actor(auth),id,input.expectedLockVersion(),true));}
 @PostMapping("/admin/reading/quizzes/{id}/unpublish")public ResponseEntity<?> unpublish(Authentication auth,@PathVariable String id,@RequestBody LockInput input){return reply(200,service.status(actor(auth),id,input.expectedLockVersion(),false));}
 @PostMapping("/reading/articles/{a}/versions/{v}/quiz/guest-sessions")public ResponseEntity<?> start(Authentication auth,@PathVariable String a,@PathVariable String v,@RequestBody GuestStart input,HttpServletRequest request){return reply(201,guests.start(actor(auth),a,v,input,request.getRemoteAddr()));}
 @PostMapping("/reading/quiz-guest/submit")public ResponseEntity<?> submit(@RequestBody GuestSubmit input,HttpServletRequest request){return reply(200,guests.submit(input,request.getRemoteAddr()));}
}
