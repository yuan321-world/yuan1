package com.englishhero.identity.admin;
import com.englishhero.identity.SessionPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/admin/users")
public class AdminUserController {
 private final AdminUserService users;private final AdminUserCommandService commands;
 public AdminUserController(AdminUserService users,AdminUserCommandService commands){this.users=users;this.commands=commands;}
 static SessionPrincipal actor(Authentication a){return a!=null&&a.getPrincipal() instanceof SessionPrincipal p?p:null;}
 static ResponseEntity<?> reply(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
 @GetMapping public ResponseEntity<?> list(Authentication a,@RequestParam(required=false)String keyword,@RequestParam(defaultValue="ALL")String status,@RequestParam(defaultValue="ALL")String role,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return reply(users.list(actor(a),keyword,status,role,page,pageSize));}
 @GetMapping("/{id}")public ResponseEntity<?> detail(Authentication a,@PathVariable String id){return reply(users.detail(actor(a),id));}
 @PutMapping("/{id}/status")public ResponseEntity<?> change(Authentication a,@PathVariable String id,@RequestBody java.util.Map<String,Object> body){
  if(!(body.get("enabled") instanceof Boolean enabled))throw com.englishhero.web.BusinessException.invalid("enabled","请选择真实的布尔状态");
  Object rawVersion=body.get("expectedVersion");if(!(rawVersion instanceof Integer||rawVersion instanceof Long))throw com.englishhero.web.BusinessException.invalid("expectedVersion","账号版本必须为整数");
  var input=new AdminUserDtos.StatusInput(body.get("requestId") instanceof String r?r:null,((Number)rawVersion).longValue(),enabled,body.get("reason") instanceof String r?r:null);return reply(commands.apply(actor(a),id,input));}
 @GetMapping("/requests/{id}")public ResponseEntity<?> command(Authentication a,@PathVariable String id){return reply(commands.status(actor(a),id));}
 @GetMapping("/audit")public ResponseEntity<?> audit(Authentication a,@RequestParam(required=false)String userId,@RequestParam(required=false)String cursor,@RequestParam(defaultValue="20")int pageSize){return reply(users.audit(actor(a),userId,cursor,pageSize));}
}
