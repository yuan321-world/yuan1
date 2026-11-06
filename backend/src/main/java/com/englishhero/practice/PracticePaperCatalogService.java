package com.englishhero.practice;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import com.englishhero.identity.*;
import com.englishhero.reading.ArticleRules;
import com.englishhero.web.BusinessException;
import static com.englishhero.practice.PracticePaperDtos.*;

@Service @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
public class PracticePaperCatalogService {
 private final PracticePaperMapper papers;private final PracticePaperContentService content;private final IdentityService identities;
 public PracticePaperCatalogService(PracticePaperMapper papers,PracticePaperContentService content,IdentityService identities){this.papers=papers;this.content=content;this.identities=identities;}
 public static PaperQuery query(Map<String,String> q){String exam=q.getOrDefault("exam","CET4");if(!Set.of("CET4","CET6").contains(exam))throw BusinessException.invalid("exam","考试类别无效");return new PaperQuery(exam,number(q,"year",2000,2100,null),number(q,"month",1,12,null),number(q,"page",1,100000,1),number(q,"pageSize",1,50,20));}
 private static Integer number(Map<String,String> q,String key,int min,int max,Integer fallback){var s=q.get(key);if(s==null||s.isBlank())return fallback;try{int n=Integer.parseInt(s);if(n>=min&&n<=max)return n;}catch(NumberFormatException ignored){}throw BusinessException.invalid(key,"筛选参数无效");}
 public PaperView view(PracticePaperMapper.Paper p,SessionPrincipal actor){var v=papers.version(p.published());var d=v.metadata();var c=papers.components(v.id());String why=content.unavailable(c);return new PaperView(""+p.id(),""+v.id(),d.title(),d.examCode(),d.year(),d.month(),d.sourceNote(),p.status(),v.revision(),0,true,c,content.details(c),List.of(),why==null,why,actor==null?null:papers.activeId(actor.userId(),p.id()));}
 public PaperView get(SessionPrincipal actor,String id){if(actor!=null)identities.current(actor);var p=papers.paper(ArticleRules.id(id,"paperId"),false);if(!"PUBLISHED".equals(p.status()))throw PracticeAdminService.missing();return view(p,actor);}
 public Catalog list(SessionPrincipal actor,PaperQuery q){
  if(actor!=null)identities.current(actor);
  var base=papers.publishedCatalogVersions().stream().filter(v->q.exam().equals(v.metadata().examCode())).toList();
  var filtered=base.stream().filter(v->(q.year()==null||q.year().equals(v.metadata().year()))&&(q.month()==null||q.month().equals(v.metadata().month()))).toList();
  var years=facets(base.stream().filter(v->q.month()==null||q.month().equals(v.metadata().month())).map(v->""+v.metadata().year()).toList());
  var months=facets(base.stream().filter(v->q.year()==null||q.year().equals(v.metadata().year())).map(v->""+v.metadata().month()).toList());
  var page=filtered.stream().skip((long)(q.page()-1)*q.pageSize()).limit(q.pageSize()).toList();
  var components=papers.catalogComponents(page.stream().map(PracticePaperMapper.Version::id).toList()).stream().collect(java.util.stream.Collectors.groupingBy(PracticePaperMapper.CatalogComponent::paperVersion));
  var active=actor==null?Map.<Long,String>of():papers.catalogActiveIds(actor.userId(),page.stream().map(PracticePaperMapper.Version::paper).toList());
  var items=page.stream().map(v->{var d=v.metadata();var c=components.getOrDefault(v.id(),List.of());String why=c.stream().allMatch(PracticePaperMapper.CatalogComponent::available)?null:"组成题组已下架，已有练习仍可继续";return new PaperView(""+v.paper(),""+v.id(),d.title(),d.examCode(),d.year(),d.month(),d.sourceNote(),"PUBLISHED",v.revision(),0,true,c.stream().map(PracticePaperMapper.CatalogComponent::input).toList(),c.stream().map(PracticePaperMapper.CatalogComponent::detail).toList(),List.of(),why==null,why,active.get(v.paper()));}).toList();
  return new Catalog(items,q.page(),q.pageSize(),filtered.size(),years,months);
 }
 private List<Facet> facets(List<String> values){var counts=new TreeMap<Integer,Long>(Comparator.reverseOrder());for(String v:values)counts.merge(Integer.parseInt(v),1L,Long::sum);return counts.entrySet().stream().map(e->new Facet(""+e.getKey(),e.getValue())).toList();}
}
