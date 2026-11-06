package com.englishhero.reading;

import com.englishhero.identity.IdentityService;
import com.englishhero.identity.SessionPrincipal;
import com.englishhero.web.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.englishhero.reading.ReadingDtos.*;

@Service
public class ReadingProgressService {
    private final ReadingMapper reading;
    private final AdminArticleService versions;
    private final IdentityService identities;
    private final com.englishhero.reading.checkin.CheckinService checkins;
    public ReadingProgressService(ReadingMapper reading,AdminArticleService versions,IdentityService identities,com.englishhero.reading.checkin.CheckinService checkins){this.reading=reading;this.versions=versions;this.identities=identities;this.checkins=checkins;}

    @Transactional
    public ProgressDto save(SessionPrincipal actor,String versionId,ProgressUpdate input){
        identities.current(actor);
        long vid=ArticleRules.id(versionId,"versionId");
        checkins.lockUser(actor.userId());
        var owned=reading.progressCurrent(actor.userId(),vid);
        if(owned==null)throw new BusinessException(404,"READING_NOT_FOUND","尚无此文章的本人阅读记录");
        if(input==null)throw BusinessException.invalid("sentenceKey","请选择阅读位置");
        ArticleRules.lock(input.expectedLockVersion());
        if(input.offset()==null||input.offset()<0)throw BusinessException.invalid("offset","阅读位置无效");
        if(input.completed()==null)throw BusinessException.invalid("completed","请指定阅读状态");
        var version=versions.version(Long.parseLong(owned.articleId()),vid);
        var sentence=version.content().paragraphs().stream().flatMap(p->p.sentences().stream()).filter(s->s.key().equals(input.sentenceKey())).findFirst().orElseThrow(()->BusinessException.invalid("sentenceKey","阅读位置不属于此文章版本"));
        int offset=input.offset();String text=sentence.text();
        if(offset>text.length()||(offset>0&&offset<text.length()&&Character.isHighSurrogate(text.charAt(offset-1))&&Character.isLowSurrogate(text.charAt(offset))))throw BusinessException.invalid("offset","阅读位置超出句子边界");
        if(reading.save(actor.userId(),vid,input)!=1)throw new BusinessException(409,"READING_PROGRESS_CONFLICT","阅读进度已在其他页面更新，请重新加载最新进度");
        if(input.completed()&&!owned.status().equals("COMPLETED"))checkins.completed(actor.userId(),vid);
        return reading.progress(actor.userId(),vid);
    }

    @Transactional(readOnly=true)
    public PageDto<ReadingHistoryDto> history(SessionPrincipal actor,int page,int size){
        return history(actor,null,null,null,"recent",page,size);
    }
    @Transactional(readOnly=true)
    public PageDto<ReadingHistoryDto> history(SessionPrincipal actor,String categoryId,String keyword,String difficulty,String sort,int page,int size){
        identities.current(actor);ArticleRules.page(page);ArticleRules.pageSize(size);
        Long category=categoryId==null||categoryId.isBlank()?null:ArticleRules.id(categoryId,"categoryId");
        String query=ArticleContent.text(keyword,"keyword",80,false);
        if(difficulty!=null&&!difficulty.isBlank()&&!java.util.Set.of("B1","B2").contains(difficulty))throw BusinessException.invalid("difficulty","请选择B1或B2");
        if(!java.util.Set.of("recent","newest","short","long").contains(sort))throw BusinessException.invalid("sort","请选择有效排序方式");
        var items=reading.history(actor.userId(),category,query,difficulty,sort,page,size).stream().map(p->{
            var v=versions.version(Long.parseLong(p.articleId()),Long.parseLong(p.articleVersionId()));
            var summary=new ArticleSummaryDto(p.articleId(),v.id(),v.title(),v.titleZh(),v.summary(),v.category(),v.difficulty(),v.wordCount(),v.estimatedMinutes(),v.author(),v.sourceType(),v.sourceUrl(),v.usageNote(),p);
            return new ReadingHistoryDto(summary,p);
        }).toList();
        return new PageDto<>(items,page,size,reading.historyCount(actor.userId(),category,query,difficulty));
    }
}
