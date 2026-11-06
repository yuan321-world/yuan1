package com.englishhero.practice;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.ObjectMapper;
import com.englishhero.web.BusinessException;
import static com.englishhero.practice.PracticeDtos.*;
@RestController @RequestMapping("/api/v1/admin/practice/import")
public class PracticeImportController {
 private final PracticeImportService service;private final ObjectMapper json;
 public PracticeImportController(PracticeImportService service,ObjectMapper json){this.service=service;this.json=json;}
 private ImportInput body(HttpServletRequest request){try{byte[] bytes=request.getInputStream().readNBytes(256*1024+1);if(bytes.length>256*1024)throw new BusinessException(413,"PRACTICE_IMPORT_TOO_LARGE","单组JSON不能超过256KiB");return json.readValue(new String(bytes,StandardCharsets.UTF_8),ImportInput.class);}catch(IOException|tools.jackson.core.JacksonException e){throw BusinessException.invalid("json","JSON格式或字段不正确，请按单组格式填写，不要传入校对状态");}}
 @PostMapping("/validate")public ResponseEntity<?> validate(Authentication a,HttpServletRequest request){return PracticeAdminController.reply(service.validate(PracticeAdminController.actor(a),body(request)));}
 @PostMapping public ResponseEntity<?> confirm(Authentication a,HttpServletRequest request){return PracticeAdminController.reply(service.importDraft(PracticeAdminController.actor(a),body(request)));}
}
