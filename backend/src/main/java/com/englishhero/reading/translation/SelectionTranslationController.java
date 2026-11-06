package com.englishhero.reading.translation;
import org.springframework.web.bind.annotation.*;import org.springframework.http.*;import org.springframework.security.core.Authentication;import com.englishhero.identity.SessionPrincipal;
@RestController @RequestMapping("/api/v1/reading/selection-translations")
public class SelectionTranslationController {
 private final SelectionTranslationService service;public SelectionTranslationController(SelectionTranslationService s){service=s;}
 private SessionPrincipal actor(Authentication a){return a!=null&&a.getPrincipal() instanceof SessionPrincipal p?p:null;}
 private ResponseEntity<?> reply(Object v){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(v);}
 @PostMapping public ResponseEntity<?> create(Authentication a,@RequestBody SelectionTranslationDtos.Input input){return reply(service.create(actor(a),input));}
 @PostMapping("/reference")public ResponseEntity<?> reference(Authentication a,@RequestBody SelectionTranslationDtos.Input input){return reply(service.reference(actor(a),input));}
 @GetMapping("/{id}")public ResponseEntity<?> get(Authentication a,@PathVariable String id){return reply(service.get(actor(a),id));}
 @GetMapping("/requests/{id}")public ResponseEntity<?> request(Authentication a,@PathVariable String id){return reply(service.request(actor(a),id));}
}
