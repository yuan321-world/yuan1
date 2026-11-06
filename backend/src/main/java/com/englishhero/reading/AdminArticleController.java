package com.englishhero.reading;
import com.englishhero.identity.SessionPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static com.englishhero.reading.ReadingDtos.*;
@RestController
@RequestMapping("/api/v1/admin/articles")
public class AdminArticleController {
    private final AdminArticleService service;
    public AdminArticleController(AdminArticleService service){this.service=service;}
    @GetMapping("/categories") public ResponseEntity<?> categories(Authentication auth){return reply(200,service.categories(actor(auth)));}
    @GetMapping public ResponseEntity<?> list(Authentication auth,@RequestParam(required=false)String status,@RequestParam(required=false)String categoryId,@RequestParam(required=false)String keyword,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return reply(200,service.list(actor(auth),status,categoryId,keyword,page,pageSize));}
    @PostMapping public ResponseEntity<?> create(Authentication auth,@RequestBody DraftInput input){return reply(201,service.create(actor(auth),input));}
    @GetMapping("/{id}") public ResponseEntity<?> get(Authentication auth,@PathVariable String id){return reply(200,service.get(actor(auth),id));}
    @PutMapping("/{id}/draft") public ResponseEntity<?> save(Authentication auth,@PathVariable String id,@RequestBody DraftUpdate input){return reply(200,service.saveDraft(actor(auth),id,input));}
    @GetMapping("/{id}/preview") public ResponseEntity<?> preview(Authentication auth,@PathVariable String id,@RequestParam String versionId){return reply(200,service.preview(actor(auth),id,versionId));}
    @PostMapping("/{id}/publish") public ResponseEntity<?> publish(Authentication auth,@PathVariable String id,@RequestBody LockInput input){return reply(200,service.publish(actor(auth),id,input.expectedLockVersion()));}
    @PostMapping("/{id}/unpublish") public ResponseEntity<?> unpublish(Authentication auth,@PathVariable String id,@RequestBody LockInput input){return reply(200,service.unpublish(actor(auth),id,input.expectedLockVersion()));}
    private SessionPrincipal actor(Authentication a){return (SessionPrincipal)a.getPrincipal();}
    private ResponseEntity<?> reply(int status,Object body){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);}
}
