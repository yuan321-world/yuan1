package com.englishhero.reading.writing;
import com.englishhero.identity.SessionPrincipal;
import com.englishhero.reading.ReadingDtos.LockInput;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static com.englishhero.reading.writing.WritingDtos.*;
@RestController @RequestMapping("/api/v1")
public class WritingController {
 private final WritingService service;public WritingController(WritingService service){this.service=service;}
 private SessionPrincipal actor(Authentication a){return a!=null&&a.getPrincipal() instanceof SessionPrincipal p?p:null;}
 private ResponseEntity<?> reply(int status,Object body){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);}
 @GetMapping("/reading/articles/{article}/versions/{version}/writing-materials")public ResponseEntity<?> published(Authentication a,@PathVariable String article,@PathVariable String version){return reply(200,service.published(actor(a),article,version));}
 @GetMapping("/admin/articles/{article}/versions/{version}/writing-materials")public ResponseEntity<?> list(Authentication a,@PathVariable String article,@PathVariable String version){return reply(200,service.list(actor(a),article,version));}
 @PostMapping("/admin/articles/{article}/versions/{version}/writing-materials")public ResponseEntity<?> create(Authentication a,@PathVariable String article,@PathVariable String version,@RequestBody Input input){return reply(201,service.create(actor(a),article,version,input));}
 @PutMapping("/admin/writing-materials/{id}")public ResponseEntity<?> save(Authentication a,@PathVariable String id,@RequestBody Input input){return reply(200,service.save(actor(a),id,input));}
 @PostMapping("/admin/writing-materials/{id}/publish")public ResponseEntity<?> publish(Authentication a,@PathVariable String id,@RequestBody LockInput input){return reply(200,service.status(actor(a),id,input==null?null:input.expectedLockVersion(),"PUBLISHED"));}
 @PostMapping("/admin/writing-materials/{id}/unpublish")public ResponseEntity<?> unpublish(Authentication a,@PathVariable String id,@RequestBody LockInput input){return reply(200,service.status(actor(a),id,input==null?null:input.expectedLockVersion(),"UNLISTED"));}
 @DeleteMapping("/admin/writing-materials/{id}")public ResponseEntity<?> archive(Authentication a,@PathVariable String id,@RequestBody LockInput input){service.status(actor(a),id,input==null?null:input.expectedLockVersion(),"ARCHIVED");return reply(204,null);}
}
