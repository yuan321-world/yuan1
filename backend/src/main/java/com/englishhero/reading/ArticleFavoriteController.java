package com.englishhero.reading;
import com.englishhero.identity.SessionPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/reading")
public class ArticleFavoriteController {
    private final ArticleFavoriteService service;
    public ArticleFavoriteController(ArticleFavoriteService service){this.service=service;}
    @PutMapping("/articles/{id}/favorite")public ResponseEntity<?> add(Authentication a,@PathVariable String id){return reply(service.set((SessionPrincipal)a.getPrincipal(),id,true));}
    @DeleteMapping("/articles/{id}/favorite")public ResponseEntity<?> remove(Authentication a,@PathVariable String id){return reply(service.set((SessionPrincipal)a.getPrincipal(),id,false));}
    @GetMapping("/favorites/states")public ResponseEntity<?> states(Authentication a,@RequestParam String ids){return reply(service.states((SessionPrincipal)a.getPrincipal(),ids));}
    @GetMapping("/favorites")public ResponseEntity<?> list(Authentication a,@RequestParam(required=false)String categoryId,@RequestParam(required=false)String keyword,@RequestParam(required=false)String difficulty,@RequestParam(defaultValue="newest")String sort,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return reply(service.list((SessionPrincipal)a.getPrincipal(),categoryId,keyword,difficulty,sort,page,pageSize));}
    private ResponseEntity<?> reply(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
