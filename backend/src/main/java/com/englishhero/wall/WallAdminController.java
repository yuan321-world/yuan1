package com.englishhero.wall;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import static com.englishhero.wall.WallDtos.*;
@RestController @RequestMapping("/api/v1/admin/wall")
public class WallAdminController {
 private final WallService messages;private final WallModerationService moderation;private final WallCommandService commands;
 public WallAdminController(WallService messages,WallModerationService moderation,WallCommandService commands){this.messages=messages;this.moderation=moderation;this.commands=commands;}
 @GetMapping("/messages")public ResponseEntity<?> list(Authentication a,@RequestParam(required=false)String status,@RequestParam(defaultValue="false")boolean flagged,@RequestParam(required=false)String cursor,@RequestParam(defaultValue="20")int pageSize){return WallController.reply(moderation.list(WallController.actor(a),status,flagged,cursor,pageSize));}
 @GetMapping("/messages/{id}")public ResponseEntity<?> detail(Authentication a,@PathVariable String id){return WallController.reply(moderation.detail(WallController.actor(a),id));}
 @GetMapping("/comments")public ResponseEntity<?> comments(Authentication a,@RequestParam String messageId,@RequestParam(required=false)String cursor,@RequestParam(defaultValue="20")int pageSize){return WallController.reply(moderation.comments(WallController.actor(a),messageId,cursor,pageSize));}
 @GetMapping("/reports")public ResponseEntity<?> reports(Authentication a,@RequestParam(required=false)String cursor,@RequestParam(defaultValue="20")int pageSize){return WallController.reply(moderation.reports(WallController.actor(a),cursor,pageSize));}
 @PostMapping("/messages")public ResponseEntity<?> create(Authentication a,@RequestBody MessageInput input){var actor=WallController.actor(a);commands.requireAdmin(actor);if(!"ANNOUNCEMENT".equals(input.kind()))throw com.englishhero.web.BusinessException.invalid("kind","请选择公告类型");return WallController.reply(messages.create(actor,input));}
 @PostMapping("/messages/{id}/moderation")public ResponseEntity<?> message(Authentication a,@PathVariable String id,@RequestBody ModerationInput input){return WallController.reply(moderation.apply(WallController.actor(a),id,false,input));}
 @PostMapping("/comments/{id}/moderation")public ResponseEntity<?> comment(Authentication a,@PathVariable String id,@RequestBody ModerationInput input){return WallController.reply(moderation.apply(WallController.actor(a),id,true,input));}
}
