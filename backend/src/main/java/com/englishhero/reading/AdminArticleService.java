package com.englishhero.reading;
import com.englishhero.identity.*;
import com.englishhero.web.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static com.englishhero.reading.ReadingDtos.*;
@Service
public class AdminArticleService {
    private final ArticleMapper mapper;private final IdentityService identities;private final IdentityMapper users;private final ObjectMapper json;
    public AdminArticleService(ArticleMapper mapper,IdentityService identities,IdentityMapper users,ObjectMapper json){this.mapper=mapper;this.identities=identities;this.users=users;this.json=json;}
    public void requireAdmin(SessionPrincipal actor){identities.current(actor);if(!users.roles(actor.userId()).contains("ADMIN"))throw new BusinessException(403,"FORBIDDEN","没有文章管理权限");}
    public List<CategoryDto> categories(SessionPrincipal actor){requireAdmin(actor);return mapper.categories();}
    public PageDto<AdminArticleDto> list(SessionPrincipal actor,String status,String categoryId,String keyword,int page,int size){
        requireAdmin(actor);ArticleRules.page(page);ArticleRules.pageSize(size);
        if(status!=null&&!status.isBlank()&&!Set.of("DRAFT","PUBLISHED","UNLISTED").contains(status))throw BusinessException.invalid("status","请选择有效状态");
        String query=ArticleContent.text(keyword,"keyword",80,false);Long category=categoryId==null||categoryId.isBlank()?null:ArticleRules.id(categoryId,"categoryId");
        return new PageDto<>(mapper.adminList(status,category,query,page,size).stream().map(this::dto).toList(),page,size,mapper.adminCount(status,category,query));
    }
    @Transactional public AdminArticleDto create(SessionPrincipal actor,DraftInput input){
        requireAdmin(actor);var draft=valid(input);long article=mapper.insertArticle(Long.parseLong(draft.categoryId()),actor.userId());
        long version=insertVersion(actor,article,1,draft);mapper.initialDraft(article,version);return get(actor,Long.toString(article));
    }
    @Transactional public AdminArticleDto saveDraft(SessionPrincipal actor,String id,DraftUpdate input){
        requireAdmin(actor);ArticleRules.lock(input.expectedLockVersion());var draft=valid(input.input());long aid=ArticleRules.id(id,"articleId");var article=mapper.article(aid,true);
        if(article==null)throw BusinessException.missing();if(mapper.practiceOnly(aid))throw BusinessException.invalid("articleId","专项原文请在专项题组中修改");if(article.lockVersion()!=input.expectedLockVersion())throw BusinessException.conflict();
        int version=mapper.version(aid,article.draftVersionId()).versionNo()+1;long vid=insertVersion(actor,aid,version,draft);
        if(mapper.saveDraft(aid,vid,input.expectedLockVersion(),actor.userId())!=1)throw BusinessException.conflict();return dto(mapper.article(aid,false));
    }
    public AdminArticleDto get(SessionPrincipal actor,String id){requireAdmin(actor);var row=mapper.article(ArticleRules.id(id,"articleId"),false);if(row==null)throw BusinessException.missing();return dto(row);}
    public ArticleVersionDto preview(SessionPrincipal actor,String id,String versionId){requireAdmin(actor);return version(ArticleRules.id(id,"articleId"),ArticleRules.id(versionId,"versionId"));}
    @Transactional public AdminArticleDto publish(SessionPrincipal actor,String id,Integer expected){
        requireAdmin(actor);ArticleRules.lock(expected);var a=mapper.article(ArticleRules.id(id,"articleId"),true);
        if(a==null)throw BusinessException.missing();if(mapper.practiceOnly(a.id()))throw BusinessException.invalid("articleId","专项材料请在专项题组内发布，不作为阅读文章发布");if(a.lockVersion()!=expected)throw BusinessException.conflict();
        var v=version(a.id(),a.draftVersionId());if(v.wordCount()<1)throw BusinessException.invalid("body","请提供可阅读的英文正文");
        if(mapper.publish(a.id(),Long.parseLong(v.id()),Long.parseLong(v.category().id()),expected,actor.userId())!=1)throw BusinessException.conflict();return dto(mapper.article(a.id(),false));
    }
    @Transactional public AdminArticleDto unpublish(SessionPrincipal actor,String id,Integer expected){
        requireAdmin(actor);ArticleRules.lock(expected);var a=mapper.article(ArticleRules.id(id,"articleId"),true);
        if(a==null||a.status().equals("DRAFT"))throw BusinessException.missing();if(mapper.practiceOnly(a.id()))throw BusinessException.invalid("articleId","专项题组请在专项管理中下架");if(a.lockVersion()!=expected)throw BusinessException.conflict();
        if(mapper.unpublish(a.id(),expected,actor.userId())!=1)throw BusinessException.conflict();return dto(mapper.article(a.id(),false));
    }
    private DraftInput valid(DraftInput d){var value=ArticleRules.validate(d);if(mapper.category(Long.parseLong(value.categoryId()))==null)throw BusinessException.invalid("categoryId","请选择有效分类");return value;}
    private long insertVersion(SessionPrincipal actor,long article,int version,DraftInput draft){
        var document=ArticleContent.normalize(draft.body());String content=json.writeValueAsString(document);String hash;
        try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("Content hash unavailable");}
        return mapper.insertVersion(article,version,actor.userId(),draft,content,hash,ArticleContent.wordCount(document));
    }
    public ArticleVersionDto version(long article,long version){return versionDto(mapper.version(article,version));}
    public ArticleVersionDto versionCurrent(long article,long version){return versionDto(mapper.versionCurrent(article,version));}
    private ArticleVersionDto versionDto(ArticleMapper.VersionRow v){if(v==null)throw BusinessException.missing();return new ArticleVersionDto(Long.toString(v.id()),Long.toString(v.articleId()),v.versionNo(),v.title(),v.titleZh(),v.summary(),mapper.category(v.categoryId()),v.difficulty(),json.readValue(v.contentJson(),ArticleContent.Document.class),v.wordCount(),v.estimatedMinutes(),v.author(),v.sourceType(),v.sourceUrl(),v.usageNote());}
    private AdminArticleDto dto(ArticleMapper.ArticleRow a){return new AdminArticleDto(Long.toString(a.id()),a.status(),a.draftVersionId()==null?null:a.draftVersionId().toString(),a.publishedVersionId()==null?null:a.publishedVersionId().toString(),a.lockVersion(),a.draftVersionId()==null?null:version(a.id(),a.draftVersionId()));}
}
