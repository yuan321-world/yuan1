package com.englishhero.reading;
import com.englishhero.identity.SessionPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/reading")
public class ReadingController {
    private final ReadingService service;
    private final ReadingProgressService progress;
    private final RecommendationService recommendations;
    public ReadingController(ReadingService service,ReadingProgressService progress,RecommendationService recommendations){this.service=service;this.progress=progress;this.recommendations=recommendations;}
    @GetMapping("/recommendation")public ResponseEntity<?> recommendation(Authentication a){return reply(200,recommendations.recommend(actor(a)));}
    @PutMapping("/progress/{versionId}") public ResponseEntity<?> save(Authentication a,@PathVariable String versionId,@RequestBody ReadingDtos.ProgressUpdate input){return reply(200,progress.save(actor(a),versionId,input));}
    @GetMapping("/history") public ResponseEntity<?> history(Authentication a,@RequestParam(required=false)String categoryId,@RequestParam(required=false)String keyword,@RequestParam(required=false)String difficulty,@RequestParam(defaultValue="recent")String sort,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return reply(200,progress.history(actor(a),categoryId,keyword,difficulty,sort,page,pageSize));}
    @GetMapping("/categories") public ResponseEntity<?> categories(Authentication a){return reply(200,service.categories(actor(a)));}
    @GetMapping("/articles") public ResponseEntity<?> list(Authentication a,@RequestParam(required=false)String categoryId,@RequestParam(required=false)String keyword,@RequestParam(required=false)String difficulty,@RequestParam(defaultValue="newest")String sort,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return reply(200,service.list(actor(a),categoryId,keyword,difficulty,sort,page,pageSize,null));}
    @GetMapping("/articles/{id}")public ResponseEntity<?> current(Authentication a,@PathVariable String id){return reply(200,service.current(actor(a),id));}
    @PostMapping("/articles/{id}/start") public ResponseEntity<?> start(Authentication a,@PathVariable String id){var result=service.start(actor(a),id);return reply(result.created()?201:200,result);}
    @GetMapping("/articles/{id}/versions/{versionId}") public ResponseEntity<?> version(Authentication a,@PathVariable String id,@PathVariable String versionId){return reply(200,service.version(actor(a),id,versionId));}
    private SessionPrincipal actor(Authentication a){return a!=null&&a.getPrincipal() instanceof SessionPrincipal p?p:null;}
    private ResponseEntity<?> reply(int status,Object value){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(value);}
}
