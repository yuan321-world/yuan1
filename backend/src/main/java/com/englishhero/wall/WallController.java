package com.englishhero.wall;
import com.englishhero.identity.SessionPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import static com.englishhero.wall.WallDtos.*;
@RestController @RequestMapping("/api/v1/wall")
public class WallController {
 private final WallService service;private final WallCommandService commands;private final WallInteractionService interactions;private final WallNotificationService notifications;
 public WallController(WallService service,WallCommandService commands,WallInteractionService interactions,WallNotificationService notifications){this.service=service;this.commands=commands;this.interactions=interactions;this.notifications=notifications;}
 static SessionPrincipal actor(Authentication auth){return auth!=null&&auth.getPrincipal() instanceof SessionPrincipal p?p:null;}
 static ResponseEntity<?> reply(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
 @GetMapping("/messages") public ResponseEntity<?> list(Authentication a,@RequestParam(required=false)String category,@RequestParam(required=false)String keyword,@RequestParam(required=false)String cursor,@RequestParam(defaultValue="20")int pageSize,@RequestParam(defaultValue="false")boolean mine){return reply(service.list(actor(a),category,keyword,cursor,pageSize,mine));}
 @GetMapping("/messages/{id}")public ResponseEntity<?> detail(Authentication a,@PathVariable String id){return reply(service.detail(actor(a),id));}
 @GetMapping("/mine")public ResponseEntity<?> mine(Authentication a,@RequestParam(required=false)String cursor,@RequestParam(defaultValue="20")int pageSize){return reply(service.mine(actor(a),cursor,pageSize));}
 @PostMapping("/messages")public ResponseEntity<?> create(Authentication a,@RequestBody MessageInput input){return reply(service.create(actor(a),input));}
 @DeleteMapping("/messages/{id}")public ResponseEntity<?> delete(Authentication a,@PathVariable String id,@RequestBody Command input){return reply(service.deleteOwn(actor(a),id,input));}
 @GetMapping("/requests/{id}")public ResponseEntity<?> status(Authentication a,@PathVariable String id){return reply(commands.status(actor(a),id));}
 @PutMapping("/messages/{id}/like")public ResponseEntity<?> like(Authentication a,@PathVariable String id,@RequestBody LikeInput input){return reply(interactions.like(actor(a),id,input));}
 @GetMapping("/messages/{id}/comments")public ResponseEntity<?> comments(Authentication a,@PathVariable String id,@RequestParam(required=false)String cursor,@RequestParam(defaultValue="20")int pageSize){return reply(interactions.list(actor(a),id,cursor,pageSize));}
 @PostMapping("/messages/{id}/comments")public ResponseEntity<?> comment(Authentication a,@PathVariable String id,@RequestBody CommentInput input){return reply(interactions.create(actor(a),id,input));}
 @DeleteMapping("/comments/{id}")public ResponseEntity<?> deleteComment(Authentication a,@PathVariable String id,@RequestBody Command input){return reply(interactions.delete(actor(a),id,input));}
 @PostMapping("/reports")public ResponseEntity<?> report(Authentication a,@RequestBody ReportInput input){return reply(interactions.report(actor(a),input));}
 @GetMapping("/notifications")public ResponseEntity<?> notices(Authentication a,@RequestParam(required=false)String cursor,@RequestParam(defaultValue="20")int pageSize){return reply(notifications.list(actor(a),cursor,pageSize));}
 @PutMapping("/notifications/{id}/read")public ResponseEntity<?> readNotice(Authentication a,@PathVariable String id){return reply(notifications.read(actor(a),id));}
 @PutMapping("/notifications/read-all")public ResponseEntity<?> readAll(Authentication a,@RequestBody java.util.Map<String,String> input){return reply(notifications.readAll(actor(a),input));}
}
