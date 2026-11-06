package com.englishhero.practice;

import com.englishhero.web.BusinessException;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PracticeAudioStorage {
    private final Path root;
    private final long limit;
    public record StoredAudio(String key, long size, String sha256) {}
    @org.springframework.beans.factory.annotation.Autowired
    public PracticeAudioStorage(@Value("${english-hero.practice.audio-directory:../.tool-cache/practice-audio/dev}") String directory,
            @Value("${english-hero.practice.audio-max-bytes:83886080}") long limit) { this(Path.of(directory), limit); }
    public PracticeAudioStorage(Path directory, long limit) { this.root=directory.toAbsolutePath().normalize();this.limit=limit; }
    public Path path(String key) {
        if(key==null || !key.matches("[a-f0-9]{32}\\.mp3")) throw BusinessException.invalid("audioAssetId","无效音频资产");
        Path result=root.resolve(key).normalize();
        if(!result.startsWith(root)) throw BusinessException.invalid("audioAssetId","无效音频资产");
        return result;
    }
    public StoredAudio store(MultipartFile file) {
        if(file==null || file.isEmpty() || file.getSize()>limit || !Objects.toString(file.getOriginalFilename(),"").toLowerCase(Locale.ROOT).endsWith(".mp3"))
            throw BusinessException.invalid("file","请选择大小不超过80MiB的完整MP3音频");
        Path temporary=null;
        try {
            Files.createDirectories(root);
            temporary=Files.createTempFile(root,"upload-",".tmp");
            var digest=MessageDigest.getInstance("SHA-256");long size=0;
            try(var input=new BufferedInputStream(file.getInputStream());var output=Files.newOutputStream(temporary)) {
                byte[] header=input.readNBytes(10);
                boolean frame=header.length>=4 && (header[0]&255)==255 && (header[1]&224)==224 && (header[1]&6)!=0 && (header[2]&240)!=0 && (header[2]&240)!=240 && (header[2]&12)!=12;
                boolean id3=header.length==10 && header[0]=='I' && header[1]=='D' && header[2]=='3' && header[3]>=2 && header[3]<=4 && header[4]!=-1 && (header[6]&128)==0 && (header[7]&128)==0 && (header[8]&128)==0 && (header[9]&128)==0;
                if(!frame&&!id3) throw BusinessException.invalid("file","文件头不是支持的MP3，请重新选择并预听");
                output.write(header);digest.update(header);size=header.length;
                byte[] buffer=new byte[16384];int count;
                while((count=input.read(buffer))!=-1){size+=count;if(size>limit)throw BusinessException.invalid("file","音频超过大小限制");output.write(buffer,0,count);digest.update(buffer,0,count);}
            }
            String key=UUID.randomUUID().toString().replace("-","")+".mp3";
            Files.move(temporary,path(key),StandardCopyOption.ATOMIC_MOVE);
            return new StoredAudio(key,size,HexFormat.of().formatHex(digest.digest()));
        } catch(IOException|NoSuchAlgorithmException e) { throw new BusinessException(503,"PRACTICE_AUDIO_UNAVAILABLE","音频暂时无法保存，请重试"); }
        finally {if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException ignored){}}
    }
    public void discard(String key){try{Files.deleteIfExists(path(key));}catch(IOException ignored){}}
}
