package com.englishhero.reading.translation;
import java.util.*;import org.springframework.stereotype.Component;import com.englishhero.practice.*;import com.englishhero.reading.*;import com.englishhero.identity.SessionPrincipal;import com.englishhero.web.BusinessException;
@Component
public class PracticeSelectionSource {
 private final PracticeAttemptService singles;private final PracticePaperAttemptService papers;
 public PracticeSelectionSource(PracticeAttemptService singles,PracticePaperAttemptService papers){this.singles=singles;this.papers=papers;}
 public boolean present(SelectionTranslationDtos.Input in){return in.practiceAttemptId()!=null||in.paperAttemptId()!=null||in.slot()!=null;}
 public ReadingDtos.ArticleVersionDto resolve(SessionPrincipal actor,SelectionTranslationDtos.Input in){
  if(!present(in))return null;
  if(in.mode()!=null&&!"TRANSLATION".equals(in.mode()))throw BusinessException.invalid("mode","练习原文仅支持翻译");
  PracticeDtos.Attempt attempt;
  if(in.practiceAttemptId()!=null){if(in.paperAttemptId()!=null||in.slot()!=null)throw BusinessException.invalid("source","练习来源不能混用");ArticleRules.id(in.practiceAttemptId(),"practiceAttemptId");attempt=singles.get(actor,in.practiceAttemptId());}
  else {ArticleRules.id(in.paperAttemptId(),"paperAttemptId");if(in.slot()==null||!Set.of("LISTENING","CLOZE","MATCH","DETAILED_1","DETAILED_2").contains(in.slot()))throw BusinessException.invalid("slot","请选择客观题原文");var paper=papers.get(actor,in.paperAttemptId());if(!"COMPLETED".equals(paper.status()))throw locked();attempt=paper.components().stream().filter(c->c.slot().equals(in.slot())).map(PracticePaperDtos.ComponentAttempt::objective).filter(Objects::nonNull).findFirst().orElseThrow(()->BusinessException.invalid("slot","原文不属于该试卷"));}
  if(!Set.of("DETAILED","MATCH","CLOZE","LISTENING").contains(attempt.readingType()))throw BusinessException.invalid("source","该练习没有可翻译的客观题原文");
  if("LISTENING".equals(attempt.readingType())&&!"COMPLETED".equals(attempt.status()))throw locked();
  var article=attempt.article();if(article==null)throw locked();if(!Objects.equals(article.articleId(),in.articleId())||!Objects.equals(article.id(),in.versionId()))throw BusinessException.invalid("versionId","原文版本不属于本轮练习");return article;
 }
 private static BusinessException locked(){return new BusinessException(409,"PRACTICE_TRANSLATION_LOCKED","请交卷后查看原文翻译");}
}
