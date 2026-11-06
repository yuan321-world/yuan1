package com.englishhero.practice;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
@RestController @RequestMapping("/api/v1/practice")
public class PracticeCatalogController {
 private final PracticeCatalogService service;
 public PracticeCatalogController(PracticeCatalogService service){this.service=service;}
 @GetMapping("/catalog")public ResponseEntity<?> catalog(){return PracticeAdminController.reply(service.catalog());}
 @GetMapping("/sets")public ResponseEntity<?> sets(Authentication a,@RequestParam Map<String,String> p){return PracticeAdminController.reply(service.list(PracticeAdminController.actor(a),PracticeQueries.parse(p,false)));}
 @GetMapping("/facets")public ResponseEntity<?> facets(Authentication a,@RequestParam Map<String,String> p){return PracticeAdminController.reply(service.facets(PracticeAdminController.actor(a),PracticeQueries.parse(p,false)));}
 @GetMapping("/sets/{id}")public ResponseEntity<?> detail(Authentication a,@PathVariable String id){return PracticeAdminController.reply(service.detail(PracticeAdminController.actor(a),id));}
}
