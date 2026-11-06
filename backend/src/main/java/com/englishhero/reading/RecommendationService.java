package com.englishhero.reading;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.englishhero.identity.*;
@Service
public class RecommendationService {
    private final ReadingMapper mapper;private final ArticleMapper articles;private final ReadingService reading;private final IdentityService identities;private final Clock clock;
    public RecommendationService(ReadingMapper mapper,ArticleMapper articles,ReadingService reading,IdentityService identities,Clock clock){this.mapper=mapper;this.articles=articles;this.reading=reading;this.identities=identities;this.clock=clock;}
    public record RecommendationDto(LocalDate date,ReadingDtos.ArticleSummaryDto article){}
    @Transactional(readOnly=true)public RecommendationDto recommend(SessionPrincipal actor){
        if(actor!=null)identities.current(actor);LocalDate date=clock.instant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();var ids=mapper.publishedIds();
        if(ids.isEmpty())return new RecommendationDto(date,null);
        long id=ids.get(Math.floorMod(date.toEpochDay(),ids.size()));return new RecommendationDto(date,reading.summary(actor,articles.article(id,false)));
    }
}
