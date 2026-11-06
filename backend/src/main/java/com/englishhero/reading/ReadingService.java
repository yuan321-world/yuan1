package com.englishhero.reading;
import com.englishhero.identity.*;
import com.englishhero.web.BusinessException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.englishhero.reading.ReadingDtos.*;
@Service
public class ReadingService {
    private final ArticleMapper articles;private final ReadingMapper reading;private final AdminArticleService versions;private final IdentityService identities;
    public ReadingService(ArticleMapper articles,ReadingMapper reading,AdminArticleService versions,IdentityService identities){this.articles=articles;this.reading=reading;this.versions=versions;this.identities=identities;}
    public List<CategoryDto> categories(SessionPrincipal actor){if(actor!=null)identities.current(actor);return articles.categories();}
    public PageDto<ArticleSummaryDto> list(SessionPrincipal actor,String categoryId,String keyword,int page,int size){return list(actor,categoryId,keyword,null,"newest",page,size,null);}
    @Transactional(readOnly=true) public PageDto<ArticleSummaryDto> list(SessionPrincipal actor,String categoryId,String keyword,String difficulty,String sort,int page,int size,Long favoriteOwner){
        if(actor!=null)identities.current(actor);ArticleRules.page(page);ArticleRules.pageSize(size);Long category=categoryId==null||categoryId.isBlank()?null:ArticleRules.id(categoryId,"categoryId");String query=ArticleContent.text(keyword,"keyword",80,false);
        if(difficulty!=null&&!difficulty.isBlank()&&!Set.of("B1","B2").contains(difficulty))throw BusinessException.invalid("difficulty","请选择B1或B2");
        if(!Set.of("newest","short","long").contains(sort))throw BusinessException.invalid("sort","请选择有效排序方式");
        var items=reading.published(category,query,difficulty,sort,page,size,favoriteOwner).stream().map(id->summary(actor,articles.article(id,false))).toList();return new PageDto<>(items,page,size,reading.count(category,query,difficulty,favoriteOwner));
    }
    public ArticleSummaryDto summary(SessionPrincipal actor,ArticleMapper.ArticleRow a){var v=versions.version(a.id(),a.publishedVersionId());return new ArticleSummaryDto(Long.toString(a.id()),v.id(),v.title(),v.titleZh(),v.summary(),v.category(),v.difficulty(),v.wordCount(),v.estimatedMinutes(),v.author(),v.sourceType(),v.sourceUrl(),v.usageNote(),actor==null?null:reading.progress(actor.userId(),Long.parseLong(v.id())));}
    @Transactional(readOnly=true) public ReadingDetailDto current(SessionPrincipal actor,String id){
        if(actor!=null)identities.current(actor);var a=articles.article(ArticleRules.id(id,"articleId"),false);
        if(a==null||articles.practiceOnly(a.id())||!a.status().equals("PUBLISHED"))throw BusinessException.missing();
        var v=versions.version(a.id(),a.publishedVersionId());return new ReadingDetailDto(v,actor==null?null:reading.progress(actor.userId(),a.publishedVersionId()));
    }
    @Transactional public StartResult start(SessionPrincipal actor,String id){
        identities.current(actor);var a=articles.article(ArticleRules.id(id,"articleId"),true);
        if(a==null||articles.practiceOnly(a.id())||a.status().equals("DRAFT"))throw BusinessException.missing();
        if(!a.status().equals("PUBLISHED"))throw new BusinessException(409,"ARTICLE_UNAVAILABLE","文章已下架，请从本人阅读历史继续或选择其他文章");
        var v=versions.versionCurrent(a.id(),a.publishedVersionId());var p=reading.progressCurrent(actor.userId(),a.publishedVersionId());boolean created=p==null;
        if(created){reading.start(actor.userId(),a.id(),a.publishedVersionId(),v.content().paragraphs().getFirst().sentences().getFirst().key());p=reading.progress(actor.userId(),a.publishedVersionId());}
        return new StartResult(v,p,created);
    }
    @Transactional(readOnly=true) public ReadingDetailDto version(SessionPrincipal actor,String articleId,String versionId){
        if(actor!=null)identities.current(actor);long aid=ArticleRules.id(articleId,"articleId"),vid=ArticleRules.id(versionId,"versionId");var a=articles.article(aid,false);if(a==null||a.status().equals("DRAFT"))throw BusinessException.missing();
        var p=actor==null?null:reading.progress(actor.userId(),vid);if((articles.practiceOnly(a.id())||!a.status().equals("PUBLISHED")||!Objects.equals(a.publishedVersionId(),vid))&&p==null)throw BusinessException.missing();
        return new ReadingDetailDto(versions.version(aid,vid),p);
    }
}
