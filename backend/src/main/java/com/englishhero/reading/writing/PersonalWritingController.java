package com.englishhero.reading.writing;
import com.englishhero.identity.SessionPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static com.englishhero.reading.writing.WritingDtos.*;
@RestController @RequestMapping("/api/v1")
public class PersonalWritingController {
 private final PersonalWritingService service;public PersonalWritingController(PersonalWritingService service){this.service=service;}
 private SessionPrincipal actor(Authentication a){return (SessionPrincipal)a.getPrincipal();}
 private ResponseEntity<?> reply(int status,Object body){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);}
 @GetMapping("/reading/articles/{article}/versions/{version}/writing-materials/states")public ResponseEntity<?> states(Authentication a,@PathVariable String article,@PathVariable String version){return reply(200,service.states(actor(a),article,version));}
 @GetMapping("/writing-materials/{id}/personal")public ResponseEntity<?> detail(Authentication a,@PathVariable String id){return reply(200,service.detail(actor(a),id));}
 @PutMapping("/writing-materials/{id}/favorite")public ResponseEntity<?> favorite(Authentication a,@PathVariable String id,@RequestBody FavoriteInput input){return reply(200,service.favorite(actor(a),id,input));}
 @DeleteMapping("/writing-materials/{id}/favorite")public ResponseEntity<?> cancel(Authentication a,@PathVariable String id){service.cancel(actor(a),id);return reply(204,null);}
 @PutMapping("/writing-materials/{id}/imitation")public ResponseEntity<?> save(Authentication a,@PathVariable String id,@RequestBody ImitationInput input){return reply(200,service.save(actor(a),id,input));}
 @GetMapping("/account/writing-materials")public ResponseEntity<?> list(Authentication a,@RequestParam(defaultValue="favorites")String view,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="12")int pageSize){return reply(200,service.list(actor(a),view,page,pageSize));}
}
