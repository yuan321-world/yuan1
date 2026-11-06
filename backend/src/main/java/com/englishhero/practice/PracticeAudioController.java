package com.englishhero.practice;

import java.nio.file.Files;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class PracticeAudioController {
    private final PracticeAudioService service;
    public PracticeAudioController(PracticeAudioService service){this.service=service;}
    @PostMapping("/admin/practice/audio-assets") public ResponseEntity<?> upload(Authentication a,@RequestParam MultipartFile file){return PracticeAdminController.reply(service.upload(PracticeAdminController.actor(a),file));}
    @PostMapping("/admin/practice/audio-assets/{id}/playback-ticket") public ResponseEntity<?> preview(Authentication a,@PathVariable String id){return PracticeAdminController.reply(service.adminTicket(PracticeAdminController.actor(a),id));}
    @PostMapping("/practice/attempts/{id}/audio-ticket") public ResponseEntity<?> ticket(Authentication a,@PathVariable String id){return PracticeAdminController.reply(service.attemptTicket(PracticeAdminController.actor(a),id));}
    @RequestMapping(value="/practice/audio/{id}",method={RequestMethod.GET,RequestMethod.HEAD})
    public ResponseEntity<StreamingResponseBody> audio(@PathVariable String id,@RequestParam String ticket,@RequestHeader(value="Range",required=false) String range,jakarta.servlet.http.HttpServletRequest request){
        var asset=service.verify(id,ticket);long size=asset.size();long[] span;
        try{span=range==null?new long[]{0,size-1}:range(range,size);}catch(ResponseStatusException e){return ResponseEntity.status(416).header("Content-Range","bytes */"+size).header("Cache-Control","private, no-store").build();}
        var headers=new HttpHeaders();headers.setContentType(MediaType.parseMediaType("audio/mpeg"));headers.setContentLength(span[1]-span[0]+1);headers.set("Accept-Ranges","bytes");headers.setCacheControl("private, no-store");headers.set("Referrer-Policy","no-referrer");headers.set("X-Content-Type-Options","nosniff");
        if(range!=null)headers.set("Content-Range","bytes "+span[0]+"-"+span[1]+"/"+size);
        StreamingResponseBody body="HEAD".equals(request.getMethod())?null:output->{try(var input=Files.newInputStream(service.path(asset))){input.skipNBytes(span[0]);byte[] buffer=new byte[16384];long remaining=span[1]-span[0]+1;while(remaining>0){int n=input.read(buffer,0,(int)Math.min(buffer.length,remaining));if(n<0)break;output.write(buffer,0,n);remaining-=n;}}};
        return new ResponseEntity<>(body,headers,range==null?HttpStatus.OK:HttpStatus.PARTIAL_CONTENT);
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<?> tooLarge(){return ResponseEntity.status(413).header("Cache-Control","no-store").body(Map.of("code","PRACTICE_AUDIO_TOO_LARGE","message","音频文件超过80MiB限制"));}
    static long[] range(String value,long size){try{if(!value.matches("bytes=\\d*-\\d*")||size<=0)throw new IllegalArgumentException();String[] sides=value.substring(6).split("-",-1);long start,end;
        if(sides[0].isEmpty()){long suffix=Long.parseLong(sides[1]);if(suffix<=0)throw new IllegalArgumentException();start=Math.max(0,size-suffix);end=size-1;}
        else{start=Long.parseLong(sides[0]);end=sides[1].isEmpty()?size-1:Math.min(size-1,Long.parseLong(sides[1]));}
        if(start<0||start>=size||end<start)throw new IllegalArgumentException();return new long[]{start,end};
    }catch(IllegalArgumentException e){throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,"无效音频范围");}}
}
