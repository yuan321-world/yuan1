package com.englishhero.vocabulary.phrase;
import com.englishhero.identity.SessionPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static com.englishhero.vocabulary.phrase.PhraseDtos.*;
@RestController
@RequestMapping("/api/v1/vocabulary/phrases")
public class PhraseController {
 private final PhraseService service;
 public PhraseController(PhraseService service){this.service=service;}
 private SessionPrincipal actor(Authentication a){return (SessionPrincipal)a.getPrincipal();}
 private ResponseEntity<?> reply(Object result){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_JSON).body(result==null?"null":result);}
 @GetMapping public ResponseEntity<?> list(Authentication a,@RequestParam(defaultValue="")String query,@RequestParam(defaultValue="false")boolean includeCancelled,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return reply(service.list(actor(a),query,includeCancelled,page,pageSize));}
 @PostMapping public ResponseEntity<?> collect(Authentication a,@RequestBody Collect in){return reply(service.collect(actor(a),in));}
 @PatchMapping("/{id}") public ResponseEntity<?> edit(Authentication a,@PathVariable String id,@RequestBody Edit in){return reply(service.edit(actor(a),id,in));}
 @DeleteMapping("/{id}") public ResponseEntity<?> cancel(Authentication a,@PathVariable String id){return reply(service.active(actor(a),id,false));}
 @PostMapping("/{id}/restore") public ResponseEntity<?> restore(Authentication a,@PathVariable String id){return reply(service.active(actor(a),id,true));}
 @PutMapping("/{id}/learning") public ResponseEntity<?> learning(Authentication a,@PathVariable String id,@RequestBody Learning in){return reply(service.learning(actor(a),id,in));}
 @GetMapping("/practice") public ResponseEntity<?> current(Authentication a){return reply(service.current(actor(a)));}
 @PostMapping("/practice") public ResponseEntity<?> start(Authentication a,@RequestBody Start in){return reply(service.start(actor(a),in));}
 @PostMapping("/practice/{id}/commands") public ResponseEntity<?> command(Authentication a,@PathVariable String id,@RequestBody Command in){return reply(service.command(actor(a),id,in));}
}
