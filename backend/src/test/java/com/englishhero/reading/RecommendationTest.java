package com.englishhero.reading;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import com.englishhero.identity.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class RecommendationTest {
    @Test void shanghaiDateChoosesAStablePublishedArticleAndExcludesRemovedOnes(){
        var mapper=mock(ReadingMapper.class);var articles=mock(ArticleMapper.class);var reading=mock(ReadingService.class);var identities=mock(IdentityService.class);
        var actor=new SessionPrincipal(1,"fixture",0,List.of("USER"));var clock=new MutableClock();
        when(mapper.publishedIds()).thenReturn(List.of(10L,20L));
        var a=new ArticleMapper.ArticleRow(10,1,"PUBLISHED",11L,11L,0,2,clock.now);var b=new ArticleMapper.ArticleRow(20,1,"PUBLISHED",21L,21L,0,2,clock.now);
        when(articles.article(10,false)).thenReturn(a);when(articles.article(20,false)).thenReturn(b);
        var first=mock(ReadingDtos.ArticleSummaryDto.class);var second=mock(ReadingDtos.ArticleSummaryDto.class);when(reading.summary(actor,a)).thenReturn(first);when(reading.summary(actor,b)).thenReturn(second);
        var service=new RecommendationService(mapper,articles,reading,identities,clock);
        var before=service.recommend(actor);assertThat(before.date()).isEqualTo(LocalDate.of(2026,10,16));assertThat(service.recommend(actor)).isEqualTo(before);
        clock.now=Instant.parse("2026-10-16T16:00:00Z");var after=service.recommend(actor);assertThat(after.date()).isEqualTo(LocalDate.of(2026,10,17));assertThat(after.article()).isNotEqualTo(before.article());
        when(mapper.publishedIds()).thenReturn(List.of(20L));assertThat(service.recommend(actor).article()).isSameAs(second);
        when(mapper.publishedIds()).thenReturn(List.of());assertThat(service.recommend(actor).article()).isNull();
    }
    static class MutableClock extends Clock{Instant now=Instant.parse("2026-10-16T15:59:59Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}}
}
