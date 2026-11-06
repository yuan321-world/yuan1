package com.englishhero.reading.writing;
import com.englishhero.identity.SessionPrincipal;
import com.englishhero.reading.*;
import com.englishhero.web.BusinessException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import static com.englishhero.reading.writing.WritingDtos.*;
@Service
public class WritingService {
 private final WritingMapper mapper;private final AdminArticleService admin;private final ArticleMapper articles;private final ReadingService reading;private final ObjectMapper json;
 public WritingService(WritingMapper mapper,AdminArticleService admin,ArticleMapper articles,ReadingService reading,ObjectMapper json){this.mapper=mapper;this.admin=admin;this.articles=articles;this.reading=reading;this.json=json;}
 @Transactional(readOnly=true)public List<PublishedMaterial> published(SessionPrincipal actor,String article,String version){reading.version(actor,article,version);return mapper.list(ArticleRules.id(article,"articleId"),ArticleRules.id(version,"versionId"),true).stream().map(r->new PublishedMaterial(""+r.id(),""+r.articleId(),""+r.versionId(),decode(r.published()),r.revision())).sorted(Comparator.comparingInt((PublishedMaterial m)->m.content().sortOrder()).thenComparingLong(m->Long.parseLong(m.id()))).toList();}
 @Transactional(readOnly=true)public List<AdminMaterial> list(SessionPrincipal actor,String article,String version){admin.preview(actor,article,version);return mapper.list(ArticleRules.id(article,"articleId"),ArticleRules.id(version,"versionId"),false).stream().map(this::dto).toList();}
 @Transactional public AdminMaterial create(SessionPrincipal actor,String article,String version,Input input){admin.requireAdmin(actor);long aid=ArticleRules.id(article,"articleId"),vid=ArticleRules.id(version,"versionId");if(articles.article(aid,true)==null||articles.version(aid,vid)==null)throw BusinessException.missing();var content=WritingRules.content(input);if(mapper.count(aid,vid)>=50)throw BusinessException.invalid("expression","每个文章版本最多50条素材");return dto(mapper.get(mapper.create(aid,vid,json.writeValueAsString(content),actor.userId()),false));}
 @Transactional public AdminMaterial save(SessionPrincipal actor,String id,Input input){var row=owned(actor,id,input==null?null:input.expectedLockVersion());mapper.save(row.id(),json.writeValueAsString(WritingRules.content(input)),actor.userId());return dto(mapper.get(row.id(),false));}
 @Transactional public AdminMaterial status(SessionPrincipal actor,String id,Integer lock,String status){var row=owned(actor,id,lock);mapper.status(row.id(),status,actor.userId());return dto(mapper.get(row.id(),false));}
 private WritingMapper.Row owned(SessionPrincipal actor,String id,Integer lock){admin.requireAdmin(actor);var row=mapper.get(ArticleRules.id(id,"materialId"),true);if(row==null||row.status().equals("ARCHIVED"))throw new BusinessException(404,"WRITING_NOT_FOUND","素材不存在或已归档");if(lock==null||lock<0)throw BusinessException.invalid("expectedLockVersion","请提供素材版本");if(row.lockVersion()!=lock)throw new BusinessException(409,"WRITING_CONFLICT","素材已被更新，请重新加载后编辑");return row;}
 private Content decode(String s){return s==null?null:json.readValue(s,Content.class);}
 private AdminMaterial dto(WritingMapper.Row r){return new AdminMaterial(""+r.id(),""+r.articleId(),""+r.versionId(),decode(r.draft()),decode(r.published()),r.status(),r.revision(),r.lockVersion());}
}
