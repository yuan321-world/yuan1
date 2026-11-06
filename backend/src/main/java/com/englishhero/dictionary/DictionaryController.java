package com.englishhero.dictionary;
import com.englishhero.identity.SessionPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/dictionary")
public class DictionaryController {
    private final DictionaryService service;private final DictionarySearchService search;
    public DictionaryController(DictionaryService service,DictionarySearchService search){this.service=service;this.search=search;}
    @GetMapping("/search")public ResponseEntity<?> search(Authentication a,@RequestParam String q){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(search.search(a!=null&&a.getPrincipal() instanceof SessionPrincipal p?p:null,q));}
    @GetMapping("/phrase")public ResponseEntity<?> phrase(Authentication a,@RequestParam String text){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.phrase(a!=null&&a.getPrincipal() instanceof SessionPrincipal p?p:null,text));}
    @GetMapping("/lookup")public ResponseEntity<?> lookup(Authentication a,@RequestParam String word){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.lookup(a!=null&&a.getPrincipal() instanceof SessionPrincipal p?p:null,word));}
}
