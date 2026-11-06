package com.englishhero.reading;
import com.englishhero.identity.*;
import com.englishhero.web.BusinessException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleFavoriteService {
    private final JdbcTemplate jdbc;private final IdentityService identities;private final ArticleMapper articles;private final ReadingService reading;
    public ArticleFavoriteService(JdbcTemplate jdbc,IdentityService identities,ArticleMapper articles,ReadingService reading){this.jdbc=jdbc;this.identities=identities;this.articles=articles;this.reading=reading;}
    public record State(String articleId,boolean favorite){}
    @Transactional public State set(SessionPrincipal actor,String id,boolean active){
        identities.current(actor);long article=ArticleRules.id(id,"articleId");var row=articles.article(article,true);
        if(row==null||row.status().equals("DRAFT"))throw BusinessException.missing();
        if(active&&!row.status().equals("PUBLISHED"))throw new BusinessException(409,"ARTICLE_UNAVAILABLE","文章已下架，暂不能收藏");
        if(active)jdbc.update("INSERT INTO user_article_favorite(user_id,article_id,active) VALUES(?,?,TRUE) ON DUPLICATE KEY UPDATE active=TRUE",actor.userId(),article);
        else jdbc.update("UPDATE user_article_favorite SET active=FALSE WHERE user_id=? AND article_id=?",actor.userId(),article);
        return new State(id,active);
    }
    @Transactional(readOnly=true)public List<String> states(SessionPrincipal actor,String ids){
        identities.current(actor);String[] values=ids==null||ids.isBlank()?new String[0]:ids.split(",",-1);
        if(values.length>50)throw BusinessException.invalid("ids","一次最多查询50篇文章");
        if(values.length==0)return List.of();var args=new ArrayList<Object>();args.add(actor.userId());for(String id:values)args.add(ArticleRules.id(id,"ids"));
        String placeholders=String.join(",",Collections.nCopies(values.length,"?"));
        return jdbc.queryForList("SELECT article_id FROM user_article_favorite WHERE user_id=? AND active=TRUE AND article_id IN ("+placeholders+")",Long.class,args.toArray()).stream().map(Object::toString).toList();
    }
    @Transactional(readOnly=true)public ReadingDtos.PageDto<ReadingDtos.ArticleSummaryDto> list(SessionPrincipal actor,String category,String keyword,String difficulty,String sort,int page,int size){
        identities.current(actor);return reading.list(actor,category,keyword,difficulty,sort,page,size,actor.userId());
    }
}
