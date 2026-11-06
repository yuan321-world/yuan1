package com.englishhero.reading;
import com.englishhero.identity.SessionPrincipal;
import com.englishhero.vocabulary.VocabularyService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/reading/articles/{articleId}/versions/{versionId}")
public class ReadingToolsController {
    private final ReadingToolsService tools;private final VocabularyService words;
    public ReadingToolsController(ReadingToolsService tools,VocabularyService words){this.tools=tools;this.words=words;}
    private SessionPrincipal actor(Authentication a){return a!=null&&a.getPrincipal() instanceof SessionPrincipal p?p:null;}
    @GetMapping("/tools") public ResponseEntity<?> tools(Authentication a,@PathVariable String articleId,@PathVariable String versionId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tools.tools(actor(a),articleId,versionId));}
    @GetMapping("/words") public ResponseEntity<?> words(Authentication a,@PathVariable String articleId,@PathVariable String versionId,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="10")int pageSize){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(words.forVersion(actor(a),articleId,versionId,page,pageSize));}
}
