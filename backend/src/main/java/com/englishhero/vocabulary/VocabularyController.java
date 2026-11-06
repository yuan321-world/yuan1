package com.englishhero.vocabulary;
import com.englishhero.identity.SessionPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/vocabulary/words")
public class VocabularyController {
    private final VocabularyService service;
    public VocabularyController(VocabularyService service){this.service=service;}
    @PostMapping public ResponseEntity<?> collect(Authentication a,@RequestBody VocabularyDtos.CollectInput input){var result=service.collect(actor(a),input);return reply(result.created()?201:200,result);}
    @PostMapping("/from-learning") public ResponseEntity<?> collectLearning(Authentication a,@RequestBody VocabularyDtos.LearningCollectionInput input){var result=service.collectFromLearning(actor(a),input);return reply(result.created()?201:200,result);}
    @PostMapping("/from-dictionary") public ResponseEntity<?> collectDictionary(Authentication a,@RequestBody VocabularyDtos.DictionaryCollectionInput input){var result=service.collectFromDictionary(actor(a),input);return reply(result.created()?201:200,result);}
    @GetMapping public ResponseEntity<?> list(Authentication a,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="ALL")String status){return reply(200,service.search(actor(a),page,pageSize,q,status));}
    @GetMapping("/{id}") public ResponseEntity<?> detail(Authentication a,@PathVariable String id,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){return reply(200,service.detail(actor(a),id,page,pageSize));}
    @DeleteMapping("/{id}") public ResponseEntity<?> cancel(Authentication a,@PathVariable String id){service.cancel(actor(a),id);return reply(204,null);}
    private SessionPrincipal actor(Authentication a){return (SessionPrincipal)a.getPrincipal();}
    private ResponseEntity<?> reply(int status,Object value){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(value);}
}
