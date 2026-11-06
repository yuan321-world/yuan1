package com.englishhero.practice;

import com.englishhero.identity.*;
import com.englishhero.reading.*;
import com.englishhero.web.BusinessException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
public class PracticeAudioService {
    private final PracticeAudioStorage storage;
    private final PracticeAudioMapper assets;
    private final AdminArticleService admin;
    private final IdentityService identities;
    private final PracticeAttemptMapper attempts;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Clock clock;
    public record AudioAssetDto(String id,String originalName,long byteSize,String sha256,String mediaType){}
    public record AudioTicketDto(String url,Instant expiresAt,String sha256){}
    public PracticeAudioService(PracticeAudioStorage storage,PracticeAudioMapper assets,AdminArticleService admin,IdentityService identities,PracticeAttemptMapper attempts,StringRedisTemplate redis,ObjectMapper json,Clock clock){this.storage=storage;this.assets=assets;this.admin=admin;this.identities=identities;this.attempts=attempts;this.redis=redis;this.json=json;this.clock=clock;}
    @Transactional public AudioAssetDto upload(SessionPrincipal actor,MultipartFile file){
        admin.requireAdmin(actor);var saved=storage.store(file);
        try{String name=Objects.toString(file.getOriginalFilename(),"audio.mp3").replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);if(name.length()>200)name=name.substring(name.length()-200);long id=assets.insert(actor.userId(),name,saved);if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)storage.discard(saved.key());}});return new AudioAssetDto(""+id,name,saved.size(),saved.sha256(),"audio/mpeg");}
        catch(RuntimeException error){storage.discard(saved.key());throw error;}
    }
    public PracticeAudioMapper.Asset asset(long id){var a=assets.get(id);if(a==null||!Files.isRegularFile(storage.path(a.key())))throw new BusinessException(404,"PRACTICE_AUDIO_NOT_FOUND","音频文件不存在，请重新上传");return a;}
    public void requireOwnedAsset(SessionPrincipal actor,String id){var a=asset(ArticleRules.id(id,"audioAssetId"));if(a.owner()!=actor.userId())throw new BusinessException(403,"FORBIDDEN","只能使用本人上传的音频");}
    public AudioTicketDto adminTicket(SessionPrincipal actor,String id){admin.requireAdmin(actor);requireOwnedAsset(actor,id);return ticket(asset(ArticleRules.id(id,"audioAssetId")));}
    public AudioTicketDto attemptTicket(SessionPrincipal actor,String id){identities.current(actor);var row=attempts.get(actor.userId(),ArticleRules.id(id,"attemptId"),false);if(row==null)throw PracticeAdminService.missing();String value=assets.version(row.setVersion());if(value==null)throw PracticeAdminService.missing();var data=json.readValue(value,PracticeDtos.ListeningData.class);return ticket(asset(ArticleRules.id(data.audioAssetId(),"audioAssetId")));}
    AudioTicketDto authorizedTicket(long id){return ticket(asset(id));}
    private AudioTicketDto ticket(PracticeAudioMapper.Asset asset){String value=UUID.randomUUID().toString()+UUID.randomUUID();redis.opsForValue().set("english-hero:practice:audio-ticket:"+hash(value),""+asset.id(),Duration.ofMinutes(10));return new AudioTicketDto("/api/v1/practice/audio/"+asset.id()+"?ticket="+value,clock.instant().plusSeconds(600),asset.sha256());}
    public PracticeAudioMapper.Asset verify(String id,String value){if(value==null||!value.matches("[a-f0-9-]{72}"))throw denied();String authorized=redis.opsForValue().get("english-hero:practice:audio-ticket:"+hash(value));if(!id.equals(authorized))throw denied();return asset(ArticleRules.id(id,"audioAssetId"));}
    public Path path(PracticeAudioMapper.Asset asset){return storage.path(asset.key());}
    private BusinessException denied(){return new BusinessException(403,"PRACTICE_AUDIO_TICKET_EXPIRED","播放授权已过期，请重新获取音频");}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
