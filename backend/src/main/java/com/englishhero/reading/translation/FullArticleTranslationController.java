package com.englishhero.reading.translation;

import com.englishhero.identity.SessionPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reading/articles/{articleId}/versions/{versionId}/full-translation")
public class FullArticleTranslationController {
    private final FullArticleTranslationService service;

    public FullArticleTranslationController(FullArticleTranslationService service) {
        this.service = service;
    }

    private SessionPrincipal actor(Authentication auth) {
        return auth != null && auth.getPrincipal() instanceof SessionPrincipal principal ? principal : null;
    }

    @GetMapping
    public ResponseEntity<FullArticleTranslationDtos.Response> get(Authentication auth,
            @PathVariable String articleId, @PathVariable String versionId) {
        return reply(service.get(actor(auth), articleId, versionId));
    }

    @PostMapping
    public ResponseEntity<FullArticleTranslationDtos.Response> enqueue(Authentication auth,
            @PathVariable String articleId, @PathVariable String versionId) {
        return reply(service.enqueue(actor(auth), articleId, versionId));
    }

    private ResponseEntity<FullArticleTranslationDtos.Response> reply(FullArticleTranslationDtos.Response result) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
