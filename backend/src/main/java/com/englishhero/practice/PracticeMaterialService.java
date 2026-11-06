package com.englishhero.practice;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;
import com.englishhero.identity.SessionPrincipal;
import com.englishhero.reading.*;
import com.englishhero.web.BusinessException;
import static com.englishhero.practice.PracticeDtos.*;

/** Dedicated material shares immutable text storage, never the reading publication lifecycle. */
@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class PracticeMaterialService {
 private final PracticeAdminService sets;
 private final ArticleMapper articles;
 private final AdminArticleService admin;
 private final PracticeAttemptMapper requests;
 private final PracticePublicationChecks hashes;
 private final ObjectMapper json;
 public PracticeMaterialService(PracticeAdminService sets,ArticleMapper articles,AdminArticleService admin,PracticeAttemptMapper requests,PracticePublicationChecks hashes,ObjectMapper json){this.sets=sets;this.articles=articles;this.admin=admin;this.requests=requests;this.hashes=hashes;this.json=json;}

 public AdminSet create(SessionPrincipal actor,MaterialInput input){
  admin.requireAdmin(actor);var d=normalize(input);PracticeAttemptService.requestId(input.requestId());
  requests.lockUser(actor.userId());String hash=hashes.hash(List.of("MATERIAL_CREATE",d,input.body()));
  var old=requests.request(actor.userId(),input.requestId());
  if(old!=null){PracticeAttemptService.checkRequest(old,"MATERIAL",hash);return sets.get(actor,""+old.set());}
  var category=articles.categories().stream().filter(c->c.code().equals("STUDY")).findFirst().orElseGet(()->articles.categories().getFirst());
  long article=articles.insertPracticeMaterial(Long.parseLong(category.id()),actor.userId());
  long version=version(actor,article,1,category.id(),d.metadata(),input.body());articles.initialDraft(article,version);
  var result=sets.create(actor,new DraftInput(""+article,""+version,"CET4",d.readingType(),d.metadata(),d.questions(),0,d.wordBank(),d.listening()));
  requests.remember(actor.userId(),input.requestId(),"MATERIAL",hash,null,Long.parseLong(result.id()),result.lockVersion());return result;
 }

 public AdminSet save(SessionPrincipal actor,String id,MaterialInput input){
  admin.requireAdmin(actor);var d=normalize(input);var before=sets.get(actor,id);
  var a=articles.article(Long.parseLong(before.articleId()),true);
  var current=sets.get(actor,id);PracticeAdminService.lock(input.expectedLockVersion(),current.lockVersion());
  if(a==null||!articles.practiceOnly(a.id()))throw BusinessException.invalid("articleId","复用的阅读文章不在专项中修改；请在文章管理维护其版本");
  var previous=articles.version(a.id(),a.draftVersionId());
  var doc=ArticleContent.normalize(ArticleContent.text(input.body(),"body",20000,true));
  boolean same=doc.equals(json.readValue(previous.contentJson(),ArticleContent.Document.class))&&Objects.equals(previous.title(),storageTitle(d.metadata().title(),"未命名专项材料"))&&Objects.equals(previous.titleZh(),storageTitle(d.metadata().titleZh(),""))&&Objects.equals(previous.sourceType(),d.metadata().sourceKind().equals("PAST")?"THIRD_PARTY":"ORIGINAL")&&Objects.equals(Objects.toString(previous.sourceUrl(),""),d.metadata().sourceUrl());
  long version=current.articleVersionId().equals(""+a.draftVersionId())&&same?a.draftVersionId():version(actor,a.id(),previous.versionNo()+1,""+previous.categoryId(),d.metadata(),input.body());
  if(version!=a.draftVersionId()&&articles.saveDraft(a.id(),version,a.lockVersion(),actor.userId())!=1)throw BusinessException.conflict();
  return sets.save(actor,id,new DraftInput(""+a.id(),""+version,"CET4",d.readingType(),d.metadata(),d.questions(),input.expectedLockVersion(),d.wordBank(),d.listening()));
 }

 private DraftInput normalize(MaterialInput input){
  if(input==null)throw BusinessException.invalid("body","请填写专项原文");
  ArticleContent.text(input.body(),"body",20000,true);
  String readingType=input.readingType()==null||input.readingType().isBlank()?"DETAILED":input.readingType();
  return PracticeDraftRules.normalize(new DraftInput(null,null,"CET4",readingType,input.metadata(),input.questions(),input.expectedLockVersion(),input.wordBank(),input.listening()));
 }
 private String storageTitle(String text,String fallback){return text.isBlank()?fallback:text.substring(0,text.offsetByCodePoints(0,Math.min(160,text.codePointCount(0,text.length()))));}
 private long version(SessionPrincipal actor,long article,int revision,String category,Metadata metadata,String body){
  String title=storageTitle(metadata.title(),"未命名专项材料");
  String zh=storageTitle(metadata.titleZh(),"");
  var doc=ArticleContent.normalize(ArticleContent.text(body,"body",20000,true));
  int words=ArticleContent.wordCount(doc);if(words<1)throw BusinessException.invalid("body","请提供英文原文");
  var draft=new ReadingDtos.DraftInput(category,title,zh,"专项题组原文","B2",body,"专项题组材料",metadata.sourceKind().equals("PAST")?"THIRD_PARTY":"ORIGINAL",metadata.sourceUrl(),"仅用于专项练习；来源与使用依据见题组说明。");
  return articles.insertVersion(article,revision,actor.userId(),draft,json.writeValueAsString(doc),hashes.hash(doc),words);
 }
}
