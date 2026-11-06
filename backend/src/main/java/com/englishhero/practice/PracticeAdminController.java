package com.englishhero.practice;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import com.englishhero.identity.SessionPrincipal;
import com.englishhero.reading.ReadingDtos.LockInput;
import static com.englishhero.practice.PracticeDtos.*;
@RestController @RequestMapping("/api/v1/admin/practice")
public class PracticeAdminController {
 private final PracticeAdminService service;private final PracticeMaterialService materials;
 public PracticeAdminController(PracticeAdminService service,PracticeMaterialService materials){this.service=service;this.materials=materials;}
 static SessionPrincipal actor(Authentication a){return a!=null&&a.getPrincipal() instanceof SessionPrincipal p?p:null;}
 static ResponseEntity<?> reply(Object value){return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(value);}
 @PostMapping({"/material-sets","/listening-sets"})public ResponseEntity<?> createMaterial(Authentication a,@RequestBody MaterialInput d){return reply(materials.create(actor(a),d));}
 @PutMapping({"/sets/{id}/material","/sets/{id}/listening"})public ResponseEntity<?> saveMaterial(Authentication a,@PathVariable String id,@RequestBody MaterialInput d){return reply(materials.save(actor(a),id,d));}
 @GetMapping("/sets")public ResponseEntity<?> list(Authentication a,@RequestParam Map<String,String> params){return reply(service.list(actor(a),PracticeQueries.parse(params,true)));}
 @PostMapping("/sets")public ResponseEntity<?> create(Authentication a,@RequestBody DraftInput d){return reply(service.create(actor(a),d));}
 @GetMapping("/sets/{id}")public ResponseEntity<?> get(Authentication a,@PathVariable String id){return reply(service.get(actor(a),id));}
 @PutMapping("/sets/{id}")public ResponseEntity<?> save(Authentication a,@PathVariable String id,@RequestBody DraftInput d){return reply(service.save(actor(a),id,d));}
 @PostMapping("/sets/{id}/review")public ResponseEntity<?> review(Authentication a,@PathVariable String id,@RequestBody ReviewInput d){return reply(service.review(actor(a),id,d));}
 @PostMapping("/sets/{id}/publish")public ResponseEntity<?> publish(Authentication a,@PathVariable String id,@RequestBody LockInput d){return reply(service.publish(actor(a),id,d.expectedLockVersion()));}
 @PostMapping("/sets/{id}/unpublish")public ResponseEntity<?> unpublish(Authentication a,@PathVariable String id,@RequestBody LockInput d){return reply(service.unpublish(actor(a),id,d.expectedLockVersion()));}
 @GetMapping("/sets/{id}/versions")public ResponseEntity<?> versions(Authentication a,@PathVariable String id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){return reply(service.versions(actor(a),id,page,pageSize));}
 @GetMapping("/sets/{id}/versions/{revision}")public ResponseEntity<?> version(Authentication a,@PathVariable String id,@PathVariable int revision){return reply(service.version(actor(a),id,revision));}
}
