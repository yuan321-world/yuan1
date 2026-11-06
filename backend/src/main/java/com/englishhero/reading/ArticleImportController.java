package com.englishhero.reading;
import com.englishhero.identity.SessionPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/admin/articles/import")
public class ArticleImportController {
    private final ArticleImportService service;
    public ArticleImportController(ArticleImportService service){this.service=service;}
    @PostMapping("/validate")public ResponseEntity<?> validate(Authentication a,@RequestBody ArticleImportDtos.ImportPayload body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.validate((SessionPrincipal)a.getPrincipal(),body));}
    @PostMapping("/confirm")public ResponseEntity<?> confirm(Authentication a,@RequestParam String clientImportId,@RequestBody ArticleImportDtos.ImportPayload body){return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.importDrafts((SessionPrincipal)a.getPrincipal(),clientImportId,body));}
}
